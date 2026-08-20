package com.vi.atlas.workflow.entity;

import jakarta.persistence.*;

/**
 * Vertical Partitioning Entity for Execution Logs.
 * Stores heavy CLOB payloads (inputContextJson and executionTraceJson) separately
 * from the lightweight execution_logs summary table for high query performance.
 */
@Entity
@Table(name = "workflow_execution_log_details")
public class ExecutionLogDetail {

    @Id
    @Column(name = "log_id", length = 36)
    private String logId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "log_id")
    private ExecutionLog executionLog;

    @Lob
    @Column(name = "input_context_json", columnDefinition = "CLOB")
    private String inputContextJson;

    @Lob
    @Column(name = "execution_trace_json", columnDefinition = "CLOB")
    private String executionTraceJson;

    public ExecutionLogDetail() {}

    public ExecutionLogDetail(String logId, String inputContextJson, String executionTraceJson) {
        this.logId = logId;
        this.inputContextJson = inputContextJson;
        this.executionTraceJson = executionTraceJson;
    }

    public String getLogId() {
        return logId;
    }

    public void setLogId(String logId) {
        this.logId = logId;
    }

    public ExecutionLog getExecutionLog() {
        return executionLog;
    }

    public void setExecutionLog(ExecutionLog executionLog) {
        this.executionLog = executionLog;
    }

    public String getInputContextJson() {
        return inputContextJson;
    }

    public void setInputContextJson(String inputContextJson) {
        this.inputContextJson = inputContextJson;
    }

    public String getExecutionTraceJson() {
        return executionTraceJson;
    }

    public void setExecutionTraceJson(String executionTraceJson) {
        this.executionTraceJson = executionTraceJson;
    }
}
