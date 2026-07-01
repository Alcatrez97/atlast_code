package com.enterprise.atlas.workflow.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_forms")
public class CustomerForm {

    @Id
    @Column(name = "customer_form_pk", length = 36)
    private String id; // UUID, matches engine's contextId

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(name = "form_status", length = 100)
    private String formStatus; // e.g., A2 Pending, A2Accept, A2Reject

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
}
