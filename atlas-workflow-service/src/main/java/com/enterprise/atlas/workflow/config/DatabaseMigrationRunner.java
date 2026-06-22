package com.enterprise.atlas.workflow.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Component("databaseMigrationRunner")
public class DatabaseMigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationRunner.class);

    @Autowired
    private DataSource dataSource;

    @PostConstruct
    public void migrate() {
        log.info("Starting database schema migration check...");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Disable foreign key checks temporarily in H2 (if any exist) to allow renames/modifications
            try {
                stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");
            } catch (Exception e) {
                log.debug("Could not disable referential integrity: {}", e.getMessage());
            }

            // 1. Rename primary key 'id' columns if they exist
            renameColumnIfExist(stmt, "buckets", "id", "bucket_pk");
            renameColumnIfExist(stmt, "bucket_executions", "id", "bucket_execution_pk");
            renameColumnIfExist(stmt, "context_fields", "id", "context_field_pk");
            renameColumnIfExist(stmt, "context_schemas", "id", "context_schema_pk");
            renameColumnIfExist(stmt, "customer_forms", "id", "customer_form_pk");
            renameColumnIfExist(stmt, "execution_logs", "id", "execution_log_pk");
            renameColumnIfExist(stmt, "integration_registry", "id", "integration_pk");
            renameColumnIfExist(stmt, "revert_status", "id", "revert_status_pk");
            renameColumnIfExist(stmt, "rules", "id", "rule_pk");
            renameColumnIfExist(stmt, "workflow_definitions", "id", "workflow_definition_pk");
            renameColumnIfExist(stmt, "workflow_instances", "id", "workflow_instance_pk");
            renameColumnIfExist(stmt, "workflow_versions", "id", "workflow_version_pk");
            renameColumnIfExist(stmt, "event_definitions", "id", "event_definition_pk");

            // 2. Clean up orphaned foreign key data to allow FK constraint creation
            cleanOrphanedData(stmt);

            // 3. Heal historical bucket execution data inconsistencies
            healWorkloadDataInconsistency(stmt);

            // 4. Alter column length of task_instances.task_instance_pk to VARCHAR(255)
            alterColumnLengthIfExist(stmt, "task_instances", "task_instance_pk", 255);

            // Re-enable referential integrity
            try {
                stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
            } catch (Exception e) {
                log.debug("Could not enable referential integrity: {}", e.getMessage());
            }

            log.info("Database schema migration check completed successfully.");
        } catch (Exception e) {
            log.error("Failed to run database schema migrations: {}", e.getMessage(), e);
            throw new RuntimeException("Database migration failed", e);
        }
    }

    private void renameColumnIfExist(Statement stmt, String tableName, String oldColName, String newColName) throws Exception {
        String query = String.format(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE UPPER(TABLE_NAME) = '%s' AND UPPER(COLUMN_NAME) = '%s'",
                tableName.toUpperCase(), oldColName.toUpperCase()
        );

        try (ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next() && rs.getInt(1) > 0) {
                log.info("Renaming column {}.{} to {}...", tableName, oldColName, newColName);
                String sql = String.format(
                        "ALTER TABLE %s ALTER COLUMN %s RENAME TO %s",
                        tableName, oldColName, newColName
                );
                stmt.execute(sql);
                log.info("Successfully renamed column {}.{} to {}.", tableName, oldColName, newColName);
            }
        }
    }

    private void cleanOrphanedData(Statement stmt) throws Exception {
        log.info("Cleaning up orphaned records before constraint creation...");

        // Helper to check table existence
        autoCleanOrphans(stmt, "bucket_executions", "execution_log_id", "execution_logs", "execution_log_pk");
        autoCleanOrphans(stmt, "bucket_executions", "instance_id", "workflow_instances", "workflow_instance_pk");
        autoCleanOrphans(stmt, "workflow_instances", "version_id", "workflow_versions", "workflow_version_pk");
        autoCleanOrphans(stmt, "execution_logs", "version_id", "workflow_versions", "workflow_version_pk");
        autoCleanOrphans(stmt, "execution_logs", "instance_id", "workflow_instances", "workflow_instance_pk");
        autoCleanOrphans(stmt, "revert_status", "workflow_instance_id", "workflow_instances", "workflow_instance_pk");
        autoCleanOrphans(stmt, "revert_status", "form_id", "customer_forms", "customer_form_pk");

        // Clean context_fields integration_id
        if (tableExists(stmt, "context_fields") && tableExists(stmt, "integration_registry")) {
            log.info("Cleaning up invalid integration_id references in context_fields...");
            stmt.execute("UPDATE context_fields SET integration_id = NULL " +
                         "WHERE integration_id IS NOT NULL " +
                         "AND integration_id NOT IN (SELECT integration_pk FROM integration_registry)");
        }
    }

    private void autoCleanOrphans(Statement stmt, String childTable, String childFkCol, String parentTable, String parentPkCol) throws Exception {
        if (tableExists(stmt, childTable) && tableExists(stmt, parentTable)) {
            log.info("Cleaning up orphans in {} on column {}...", childTable, childFkCol);
            String sql = String.format(
                    "DELETE FROM %s WHERE %s IS NOT NULL AND %s NOT IN (SELECT %s FROM %s)",
                    childTable, childFkCol, childFkCol, parentPkCol, parentTable
            );
            int deleted = stmt.executeUpdate(sql);
            if (deleted > 0) {
                log.info("Deleted {} orphaned records from {}.", deleted, childTable);
            }
        }
    }

    private boolean tableExists(Statement stmt, String tableName) throws Exception {
        String query = String.format(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = '%s'",
                tableName.toUpperCase()
        );
        try (ResultSet rs = stmt.executeQuery(query)) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private void healWorkloadDataInconsistency(Statement stmt) throws Exception {
        if (tableExists(stmt, "bucket_executions") && tableExists(stmt, "workflow_instances")) {
            log.info("Healing bucket executions for completed workflow instances...");
            String sql1 = "UPDATE bucket_executions " +
                          "SET status = 'RESOLVED', resolved_at = CURRENT_TIMESTAMP(), resolved_by = 'DataMigrationFix', " +
                          "    resolution_notes = 'Auto-resolved: matching workflow instance is completed' " +
                          "WHERE status <> 'RESOLVED' " +
                          "AND instance_id IN (SELECT workflow_instance_pk FROM workflow_instances WHERE status = 'COMPLETED')";
            int updated1 = stmt.executeUpdate(sql1);
            if (updated1 > 0) {
                log.info("Auto-resolved {} bucket executions for completed workflow instances.", updated1);
            }
        }

        if (tableExists(stmt, "bucket_executions") && tableExists(stmt, "revert_status")) {
            log.info("Healing bucket executions for completed reverts...");
            String sql2 = "UPDATE bucket_executions be " +
                          "SET be.status = 'RESOLVED', be.resolved_at = CURRENT_TIMESTAMP(), be.resolved_by = 'DataMigrationFix', " +
                          "    be.resolution_notes = 'Auto-resolved: matching revert status is completed' " +
                          "WHERE be.status <> 'RESOLVED' " +
                          "AND EXISTS (" +
                          "    SELECT 1 FROM revert_status rs " +
                          "    WHERE rs.workflow_instance_id = be.instance_id " +
                          "      AND rs.bucket_id = be.bucket_id " +
                          "      AND rs.status = 'COMPLETED'" +
                          ")";
            int updated2 = stmt.executeUpdate(sql2);
            if (updated2 > 0) {
                log.info("Auto-resolved {} bucket executions for completed reverts.", updated2);
            }
        }
    }

    private void alterColumnLengthIfExist(Statement stmt, String tableName, String colName, int newLength) throws Exception {
        if (tableExists(stmt, tableName)) {
            log.info("Altering column {}.{} length to {}...", tableName, colName, newLength);
            String sql = String.format(
                    "ALTER TABLE %s ALTER COLUMN %s VARCHAR(%d)",
                    tableName, colName, newLength
            );
            stmt.execute(sql);
            log.info("Successfully altered column {}.{} length to {}.", tableName, colName, newLength);
        }
    }
}
