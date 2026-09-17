package com.vi.atlas.workflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "POSTPAID_ONBOARD_CAF")
public class CustomerForm {

    @Id
    @Column(name = "caf_id", length = 100)
    private String id; // UUID or tracking CAF ID, matches engine's contextId

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(name = "form_status", length = 100)
    private String formStatus; // e.g., A2 Pending, A2Accept, A2Reject

    @Column(name = "circle_id")
    private Integer circleId;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public CustomerForm() {}

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
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
