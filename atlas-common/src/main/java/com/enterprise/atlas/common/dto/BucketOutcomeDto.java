package com.enterprise.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

/**
 * Represents a possible outcome for a business outcome bucket.
 * E.g. Accept, Reject, Park, Escalate.
 */
@Schema(description = "A possible resolution outcome for an outcome bucket")
public class BucketOutcomeDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Name of the outcome (e.g. Accept, Reject, Park)", requiredMode = Schema.RequiredMode.REQUIRED, example = "Accept")
    private String name;

    @Schema(description = "Optional override for the CustomerForm status string. If null, auto-derived as {bucketId}{name} (e.g. A2Accept)", example = "A2_APPROVED")
    private String formStatus;

    public BucketOutcomeDto() {}

    public BucketOutcomeDto(String name) {
        this.name = name;
    }

    public BucketOutcomeDto(String name, String formStatus) {
        this.name = name;
        this.formStatus = formStatus;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFormStatus() { return formStatus; }
    public void setFormStatus(String formStatus) { this.formStatus = formStatus; }
}
