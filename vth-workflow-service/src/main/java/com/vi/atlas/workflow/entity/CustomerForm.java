package com.vi.atlas.workflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "POSTPAID_ONBOARD_CAF")
public class CustomerForm {

    @Id
    @Column(name = "caf_id")
    private Long id; // Numeric CAF ID, Long type

    @Column(name = "form_status", length = 100)
    private String formStatus; // e.g., A2 Pending, A2Accept, A2Reject

    @Column(name = "circle_id")
    private Integer circleId;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public CustomerForm() {}

    public CustomerForm(Long id, String formStatus, Integer circleId) {
        this.id = id;
        this.formStatus = formStatus;
        this.circleId = circleId;
    }

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

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

    /**
     * Safely converts any Object, String, or Number into a valid Long cafId.
     */
    public static Long parseCafId(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        String s = value.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            String digits = s.replaceAll("\\D+", "");
            if (!digits.isEmpty() && digits.length() <= 18) {
                try {
                    return Long.parseLong(digits);
                } catch (NumberFormatException ignored) {}
            }
            return (long) Math.abs(s.hashCode());
        }
    }
}

