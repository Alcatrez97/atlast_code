package com.enterprise.atlas.workflow.entity;

import com.enterprise.atlas.workflow.entity.converter.GenericJsonConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entity representing an individual task activity executed within a workflow instance.
 */
@Entity
@Table(name = "task_instances", indexes = {
    @Index(name = "idx_task_inst_parent", columnList = "instance_id"),
    @Index(name = "idx_task_inst_status", columnList = "status"),
    @Index(name = "idx_task_inst_type", columnList = "task_type")
})
public class TaskInstance {

    @Id
    @Column(name = "task_instance_pk", length = 255)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instance_id", nullable = false, foreignKey = @ForeignKey(name = "fk_task_inst_workflow"))
    private WorkflowInstance workflowInstance;

    @Column(name = "task_type", nullable = false, length = 50)
    private String taskType;

    @Column(name = "label", nullable = false, length = 200)
    private String label;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private WorkflowInstanceStatus status;

    @Lob
    @Column(name = "input_data", columnDefinition = "CLOB")
    @Convert(converter = GenericJsonConverter.MapConverter.class)
    private Map<String, Object> inputData;

    @Lob
    @Column(name = "output_data", columnDefinition = "CLOB")
    @Convert(converter = GenericJsonConverter.MapConverter.class)
    private Map<String, Object> outputData;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public TaskInstance() {}

    @PrePersist
    protected void onCreate() {
        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
    }

    // ---- Getters & Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public WorkflowInstance getWorkflowInstance() { return workflowInstance; }
    public void setWorkflowInstance(WorkflowInstance workflowInstance) { this.workflowInstance = workflowInstance; }

    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getStatus() { return status != null ? status.name() : null; }
    public void setStatus(String status) { this.status = status != null ? WorkflowInstanceStatus.valueOf(status.toUpperCase()) : null; }

    public Map<String, Object> getInputData() { return inputData; }
    public void setInputData(Map<String, Object> inputData) { this.inputData = inputData; }

    public Map<String, Object> getOutputData() { return outputData; }
    public void setOutputData(Map<String, Object> outputData) { this.outputData = outputData; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
