package com.enterprise.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "Data Transfer Object representing a context payload validation check outcome")
public class ValidationResultDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Flag specifying if the context payload matches schema definitions and is valid to execute", example = "true")
    private boolean valid;

    @Schema(description = "List of critical blocking validation error details", example = "[\"Field 'amount' is required but is missing\"]")
    private List<String> errors = new ArrayList<>();    // blocking issues (required fields missing, type errors)

    @Schema(description = "List of non-blocking warning validation details", example = "[\"Field 'extraParam' is unexpected in this schema\"]")
    private List<String> warnings = new ArrayList<>();  // non-blocking (optional fields absent, unexpected fields)

    public ValidationResultDto() {}

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }

    public void addError(String message) { this.errors.add(message); this.valid = false; }
    public void addWarning(String message) { this.warnings.add(message); }
}
