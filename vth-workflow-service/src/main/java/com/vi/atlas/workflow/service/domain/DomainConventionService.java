package com.vi.atlas.workflow.service.domain;

import com.vi.atlas.common.dto.ContextSchemaDto;
import com.vi.atlas.common.dto.DomainMappingDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class DomainConventionService {

    private static final Logger log = LoggerFactory.getLogger(DomainConventionService.class);

    private final List<DomainMappingDto> registry = new ArrayList<>();

    public DomainConventionService() {
        initConventions();
    }

    private void initConventions() {
        // Individual Retail Postpaid CAF
        registry.add(new DomainMappingDto(
                "CAF_",
                "Telecom Postpaid Customer Form (CAF)",
                "POSTPAID_ONBOARD_CAF",
                "caf_id",
                "form_status",
                "cafId",
                "Retail individual postpaid subscriber onboarding and CAF verification workflow."
        ));

        // Corporate Postpaid COCP
        registry.add(new DomainMappingDto(
                "COCP_",
                "Corporate Postpaid Customer Form (COCP)",
                "POSTPAID_ONBOARD_COCP",
                "cocp_id",
                "form_status",
                "cocpId",
                "Corporate Owned Corporate Paid (COCP) enterprise subscriber onboarding and verification workflow."
        ));
    }

    public List<DomainMappingDto> getAllConventions() {
        return Collections.unmodifiableList(registry);
    }

    /**
     * Resolves the target table, primary key, and status column for a given workflowKey and context schema.
     */
    public DomainMappingDto resolveDomain(String workflowKey, ContextSchemaDto schema) {
        // 1. Explicit Schema Override (Highest Priority)
        if (schema != null && schema.getTargetTable() != null && !schema.getTargetTable().isBlank()) {
            DomainMappingDto custom = new DomainMappingDto();
            custom.setPrefix(null);
            custom.setDomainName("Custom Schema Override");
            custom.setTableName(schema.getTargetTable().trim());
            custom.setPrimaryKeyColumn(schema.getTargetPkColumn() != null && !schema.getTargetPkColumn().isBlank() 
                    ? schema.getTargetPkColumn().trim() 
                    : (schema.getContextIdField() != null ? schema.getContextIdField() : "id"));
            custom.setStatusColumn(schema.getTargetStatusColumn() != null && !schema.getTargetStatusColumn().isBlank() 
                    ? schema.getTargetStatusColumn().trim() 
                    : "status");
            custom.setDefaultContextIdField(schema.getContextIdField());
            custom.setDescription("Explicit target table specified in workflow context schema.");
            custom.setMatchType("EXPLICIT_OVERRIDE");
            return custom;
        }

        // 2. Naming Convention Prefix Match
        if (workflowKey != null && !workflowKey.isBlank()) {
            String upperKey = workflowKey.trim().toUpperCase();
            for (DomainMappingDto convention : registry) {
                if (upperKey.startsWith(convention.getPrefix().toUpperCase())) {
                    DomainMappingDto match = new DomainMappingDto(
                            convention.getPrefix(),
                            convention.getDomainName(),
                            convention.getTableName(),
                            convention.getPrimaryKeyColumn(),
                            convention.getStatusColumn(),
                            convention.getDefaultContextIdField(),
                            convention.getDescription()
                    );
                    match.setMatchType("CONVENTION_MATCH");
                    return match;
                }
            }
        }

        // 3. Fallback to Default CAF Domain with a Warning flag
        DomainMappingDto fallback = new DomainMappingDto(
                "DEFAULT",
                "Default Fallback (CAF)",
                "POSTPAID_ONBOARD_CAF",
                "caf_id",
                "form_status",
                "cafId",
                "Workflow key does not match recognized prefix (CAF_, COCP_). Defaulting to POSTPAID_ONBOARD_CAF."
        );
        fallback.setMatchType("DEFAULT_FALLBACK");
        return fallback;
    }
}
