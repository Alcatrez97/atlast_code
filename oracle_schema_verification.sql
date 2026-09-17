-- =============================================================================
-- ATLAS WORKFLOW & DECISION ENGINE - ORACLE DATABASE VERIFICATION SCRIPT
-- =============================================================================
-- Target Database: Oracle Database 12c / 18c / 19c / 21c / 23c (Compatible with DBeaver)
-- Description: Automated verification script to validate table setup,
--              Primary Keys, Foreign Key relationships, orphan records,
--              and missing FK indexes across all 17 workflow tables.
-- =============================================================================

-- =============================================================================
-- 1. TABLE INVENTORY & ROW COUNTS
-- Verifies all 17 engine tables exist in your schema (16 WORKFLOW_* + POSTPAID_ONBOARD_CAF)
-- =============================================================================
SELECT 
    t.table_name,
    t.num_rows AS estimated_rows,
    t.status,
    t.last_analyzed
FROM user_tables t
WHERE t.table_name LIKE 'WORKFLOW_%' 
   OR t.table_name = 'POSTPAID_ONBOARD_CAF'
ORDER BY t.table_name;


-- =============================================================================
-- 2. FOREIGN KEY RELATIONSHIPS AUDIT
-- Verifies all FKs, source/target tables, matching columns, and status
-- =============================================================================
SELECT 
    a.table_name AS child_table,
    a.constraint_name AS fk_constraint_name,
    col_a.column_name AS fk_column,
    c_pk.table_name AS parent_table,
    col_r.column_name AS parent_pk_column,
    a.status AS constraint_status,
    a.delete_rule
FROM user_constraints a
JOIN user_cons_columns col_a 
    ON a.constraint_name = col_a.constraint_name
JOIN user_constraints c_pk 
    ON a.r_constraint_name = c_pk.constraint_name
JOIN user_cons_columns col_r 
    ON c_pk.constraint_name = col_r.constraint_name 
    AND col_a.position = col_r.position
WHERE a.constraint_type = 'R'
  AND (a.table_name LIKE 'WORKFLOW_%' OR a.table_name = 'POSTPAID_ONBOARD_CAF')
ORDER BY a.table_name, a.constraint_name;


-- =============================================================================
-- 3. PRIMARY KEY VERIFICATION
-- Ensures every workflow table has an active Primary Key
-- =============================================================================
SELECT 
    c.table_name,
    c.constraint_name AS pk_name,
    cc.column_name AS pk_column,
    c.status
FROM user_constraints c
JOIN user_cons_columns cc 
    ON c.constraint_name = cc.constraint_name
WHERE c.constraint_type = 'P'
  AND (c.table_name LIKE 'WORKFLOW_%' OR c.table_name = 'POSTPAID_ONBOARD_CAF')
ORDER BY c.table_name;


-- =============================================================================
-- 4. REFERENTIAL INTEGRITY / ORPHAN RECORD AUDIT
-- Returns 0 rows if all FK relationships are healthy and consistent
-- =============================================================================
SELECT 'workflow_versions without workflow_definition' AS issue_type, COUNT(*) AS orphan_count
FROM workflow_versions v
LEFT JOIN workflow_definitions d ON v.workflow_definition_id = d.workflow_definition_pk
WHERE d.workflow_definition_pk IS NULL

UNION ALL

SELECT 'workflow_instances without workflow_version', COUNT(*)
FROM workflow_instances i
LEFT JOIN workflow_versions v ON i.version_id = v.workflow_version_pk
WHERE v.workflow_version_pk IS NULL

UNION ALL

SELECT 'workflow_task_instances without workflow_instance', COUNT(*)
FROM workflow_task_instances t
LEFT JOIN workflow_instances i ON t.instance_id = i.workflow_instance_pk
WHERE i.workflow_instance_pk IS NULL

UNION ALL

SELECT 'workflow_event_subscriptions without workflow_instance', COUNT(*)
FROM workflow_event_subscriptions s
LEFT JOIN workflow_instances i ON s.instance_id = i.workflow_instance_pk
WHERE i.workflow_instance_pk IS NULL

UNION ALL

SELECT 'workflow_execution_logs without workflow_version', COUNT(*)
FROM workflow_execution_logs l
LEFT JOIN workflow_versions v ON l.version_id = v.workflow_version_pk
WHERE l.version_id IS NOT NULL AND v.workflow_version_pk IS NULL

UNION ALL

SELECT 'workflow_execution_log_details without parent execution_log', COUNT(*)
FROM workflow_execution_log_details d
LEFT JOIN workflow_execution_logs l ON d.log_id = l.execution_log_pk
WHERE l.execution_log_pk IS NULL

UNION ALL

SELECT 'workflow_bucket_executions without parent execution_log', COUNT(*)
FROM workflow_bucket_executions be
LEFT JOIN workflow_execution_logs l ON be.execution_log_id = l.execution_log_pk
WHERE l.execution_log_pk IS NULL

UNION ALL

SELECT 'workflow_bucket_executions without workflow_instance', COUNT(*)
FROM workflow_bucket_executions be
LEFT JOIN workflow_instances i ON be.instance_id = i.workflow_instance_pk
WHERE be.instance_id IS NOT NULL AND i.workflow_instance_pk IS NULL

UNION ALL

SELECT 'workflow_context_fields without context_schema', COUNT(*)
FROM workflow_context_fields f
LEFT JOIN workflow_context_schemas s ON f.schema_id = s.context_schema_pk
WHERE s.context_schema_pk IS NULL

UNION ALL

SELECT 'workflow_context_fields without integration_registry', COUNT(*)
FROM workflow_context_fields f
LEFT JOIN workflow_integration_registry r ON f.integration_id = r.integration_pk
WHERE f.integration_id IS NOT NULL AND r.integration_pk IS NULL

UNION ALL

SELECT 'workflow_revert_status without POSTPAID_ONBOARD_CAF', COUNT(*)
FROM workflow_revert_status r
LEFT JOIN POSTPAID_ONBOARD_CAF c ON r.form_id = c.caf_id
WHERE r.form_id IS NOT NULL AND c.caf_id IS NULL;


-- =============================================================================
-- 5. UNINDEXED FOREIGN KEYS AUDIT (Oracle Performance Best Practice)
-- Any returned row indicates an FK column lacking an index
-- =============================================================================
SELECT 
    c.table_name,
    c.constraint_name,
    cc.column_name AS missing_index_column
FROM user_constraints c
JOIN user_cons_columns cc 
    ON c.constraint_name = cc.constraint_name
WHERE c.constraint_type = 'R'
  AND (c.table_name LIKE 'WORKFLOW_%' OR c.table_name = 'POSTPAID_ONBOARD_CAF')
  AND NOT EXISTS (
      SELECT 1 
      FROM user_ind_columns ic
      WHERE ic.table_name = cc.table_name
        AND ic.column_name = cc.column_name
  )
ORDER BY c.table_name;
