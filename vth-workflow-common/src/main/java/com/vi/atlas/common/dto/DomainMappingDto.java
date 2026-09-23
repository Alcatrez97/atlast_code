package com.vi.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

@Schema(description = "Data Transfer Object representing a domain convention mapping workflow key prefixes to database tables")
public class DomainMappingDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Workflow key prefix pattern", example = "CAF_")
    private String prefix;

    @Schema(description = "Human-readable domain name", example = "Postpaid Customer Application Form")
    private String domainName;

    @Schema(description = "Database target table name", example = "POSTPAID_ONBOARD_CAF")
    private String tableName;

    @Schema(description = "Primary key column name in target table", example = "caf_id")
    private String primaryKeyColumn;

    @Schema(description = "Status column name in target table", example = "form_status")
    private String statusColumn;

    @Schema(description = "Suggested contextId field name in payload", example = "cafId")
    private String defaultContextIdField;

    @Schema(description = "Detailed explanation of this domain mapping", example = "Updated whenever workflows enter BUCKET stages or simulate approvals")
    private String description;

    @Schema(description = "Whether this convention is an exact match, prefix match, or fallback", example = "PREFIX_MATCH")
    private String matchType;

    public DomainMappingDto() {}

    public DomainMappingDto(String prefix, String domainName, String tableName, 
                            String primaryKeyColumn, String statusColumn, 
                            String defaultContextIdField, String description) {
        this.prefix = prefix;
        this.domainName = domainName;
        this.tableName = tableName;
        this.primaryKeyColumn = primaryKeyColumn;
        this.statusColumn = statusColumn;
        this.defaultContextIdField = defaultContextIdField;
        this.description = description;
        this.matchType = "CONVENTION";
    }

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }

    public String getDomainName() { return domainName; }
    public void setDomainName(String domainName) { this.domainName = domainName; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getPrimaryKeyColumn() { return primaryKeyColumn; }
    public void setPrimaryKeyColumn(String primaryKeyColumn) { this.primaryKeyColumn = primaryKeyColumn; }

    public String getStatusColumn() { return statusColumn; }
    public void setStatusColumn(String statusColumn) { this.statusColumn = statusColumn; }

    public String getDefaultContextIdField() { return defaultContextIdField; }
    public void setDefaultContextIdField(String defaultContextIdField) { this.defaultContextIdField = defaultContextIdField; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMatchType() { return matchType; }
    public void setMatchType(String matchType) { this.matchType = matchType; }
}
