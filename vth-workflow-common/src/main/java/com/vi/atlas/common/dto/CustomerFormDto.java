package com.vi.atlas.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "Data Transfer Object representing a customer form state")
public class CustomerFormDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Numeric CAF ID", example = "100234")
    private Long id;
    private String formStatus;
    private Integer circleId;
    private LocalDateTime updatedAt;

    public CustomerFormDto() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCafId() {
        return id;
    }

    public void setCafId(Long cafId) {
        this.id = cafId;
    }

    public String getFormStatus() {
        return formStatus;
    }

    public void setFormStatus(String formStatus) {
        this.formStatus = formStatus;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getCircleId() {
        return circleId;
    }

    public void setCircleId(Integer circleId) {
        this.circleId = circleId;
    }
}
