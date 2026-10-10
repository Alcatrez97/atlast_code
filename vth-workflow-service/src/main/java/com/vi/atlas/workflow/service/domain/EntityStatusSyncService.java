package com.vi.atlas.workflow.service.domain;

import com.vi.atlas.common.dto.ContextSchemaDto;
import com.vi.atlas.common.dto.DomainMappingDto;
import com.vi.atlas.workflow.entity.CustomerForm;
import com.vi.atlas.workflow.repository.CustomerFormRepository;
import com.vi.atlas.workflow.service.context.ContextSchemaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class EntityStatusSyncService {

    private static final Logger log = LoggerFactory.getLogger(EntityStatusSyncService.class);

    @Autowired
    private DomainConventionService conventionService;

    @Autowired
    private ContextSchemaService contextSchemaService;

    @Autowired
    private CustomerFormRepository customerFormRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Synchronizes entity status on BUCKET suspension or resolution based on convention or explicit schema override.
     */
    public void syncStatus(String workflowKey, String contextId, String newStatus, String bucketId) {
        syncStatus(workflowKey, contextId, newStatus, bucketId, null);
    }

    public void syncStatus(String workflowKey, String contextId, String newStatus, String bucketId, java.util.Map<String, Object> context) {
        if (contextId == null || contextId.isBlank() || workflowKey == null || workflowKey.isBlank()) {
            log.warn("Cannot sync entity status: missing contextId or workflowKey (contextId={}, workflowKey={})", 
                    contextId, workflowKey);
            return;
        }

        Optional<ContextSchemaDto> schemaOpt = contextSchemaService.getSchemaByKey(workflowKey);
        DomainMappingDto mapping = conventionService.resolveDomain(workflowKey, schemaOpt.orElse(null));

        log.info("Synchronizing domain entity status. workflowKey={}, contextId={}, newStatus={}, targetTable={}, matchType={}",
                workflowKey, contextId, newStatus, mapping.getTableName(), mapping.getMatchType());

        // 1. Telecom Postpaid CAF Domain (Default / Legacy Handler)
        if ("POSTPAID_ONBOARD_CAF".equalsIgnoreCase(mapping.getTableName())) {
            syncCustomerForm(contextId, newStatus, bucketId, context);
            return;
        }

        // 2. Generic JDBC Dynamic Entity Sync
        syncGenericEntity(mapping, contextId, newStatus);
    }

    private void syncCustomerForm(String contextId, String newStatus, String bucketId, java.util.Map<String, Object> context) {
        Long cafId = resolveCafId(contextId, context);
        if (cafId == null) {
            log.warn("Cannot sync CustomerForm because contextId '{}' and context do not contain a valid cafId", contextId);
            return;
        }
        try {
            Optional<CustomerForm> formOpt = customerFormRepository.findById(cafId);
            if (formOpt.isPresent()) {
                CustomerForm form = formOpt.get();
                form.setFormStatus(newStatus);
                customerFormRepository.save(form);
                log.info("Updated CustomerForm status to '{}' for cafId={}", newStatus, cafId);
            } else {
                Integer circleId = resolveCircleId(context);
                CustomerForm form = new CustomerForm();
                form.setId(cafId);
                form.setFormStatus(newStatus);
                form.setCircleId(circleId);
                customerFormRepository.save(form);
                log.info("Created CustomerForm with status '{}' and circleId={} for cafId={}", newStatus, circleId, cafId);
            }
        } catch (Exception e) {
            log.error("Failed updating CustomerForm for cafId={}: {}", cafId, e.getMessage(), e);
        }
    }

    private Long resolveCafId(String contextId, java.util.Map<String, Object> context) {
        if (context != null) {
            if (context.get("cafId") != null) {
                Long parsed = CustomerForm.parseCafId(context.get("cafId"));
                if (parsed != null) return parsed;
            }
            if (context.get("caf_id") != null) {
                Long parsed = CustomerForm.parseCafId(context.get("caf_id"));
                if (parsed != null) return parsed;
            }
            if (context.get("businessKey") != null) {
                Long parsed = CustomerForm.parseCafId(context.get("businessKey"));
                if (parsed != null) return parsed;
            }
        }
        return CustomerForm.parseCafId(contextId);
    }

    private Integer resolveCircleId(java.util.Map<String, Object> context) {
        if (context != null) {
            Object cid = context.get("circleId") != null ? context.get("circleId") : context.get("circle_id");
            if (cid != null) {
                Long parsed = CustomerForm.parseCafId(cid);
                if (parsed != null) return parsed.intValue();
            }
        }
        return 101;
    }

    private void syncGenericEntity(DomainMappingDto mapping, String contextId, String newStatus) {
        String table = sanitizeIdentifier(mapping.getTableName());
        String pkCol = sanitizeIdentifier(mapping.getPrimaryKeyColumn());
        String statusCol = sanitizeIdentifier(mapping.getStatusColumn());

        try {
            // First attempt to update existing record
            String updateSql = String.format("UPDATE %s SET %s = ? WHERE %s = ?", table, statusCol, pkCol);
            int rowsUpdated = jdbcTemplate.update(updateSql, newStatus, contextId);

            if (rowsUpdated > 0) {
                log.info("Successfully updated {} row(s) in table '{}' setting {}='{}' WHERE {}='{}'",
                        rowsUpdated, table, statusCol, newStatus, pkCol, contextId);
            } else {
                // If row does not exist, attempt to insert
                String insertSql = String.format("INSERT INTO %s (%s, %s) VALUES (?, ?)", table, pkCol, statusCol);
                try {
                    jdbcTemplate.update(insertSql, contextId, newStatus);
                    log.info("Inserted new row into table '{}' with {}='{}' and {}='{}'",
                            table, pkCol, contextId, statusCol, newStatus);
                } catch (Exception insertEx) {
                    log.warn("Could not insert new row into target table '{}' (it may require extra non-null columns): {}",
                            table, insertEx.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Generic domain entity update failed for table '{}' (contextId={}, status={}): {}. Workflow execution will continue.",
                    table, contextId, newStatus, e.getMessage());
        }
    }

    private String sanitizeIdentifier(String identifier) {
        if (identifier == null) return "";
        // Only allow safe alphanumeric and underscore characters for SQL identifiers
        return identifier.replaceAll("[^a-zA-Z0-9_]", "");
    }
}
