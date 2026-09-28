# Technical Low-Level Design (LLD): Atlas State Machine & Workflow Engine

This Low-Level Design (LLD) document provides technical specifications, end-to-end execution flows, entity DDL schemas, class contracts, execution algorithms, integration patterns, error handling strategies, and REST API contracts for building the **Atlas State Machine & Workflow Engine**.

---

## 1. End-to-End Workflow Flow: CAF Journey to Order Creation

To understand how the workflow engine operates in practice, consider a telecom **Customer Acquisition Form (CAF)** activation journey. The journey starts with a customer submitting a CAF, moves through manual verification queues (buckets), integrates with external telecom systems, triggers an automated **Order Creation** command, and completes.

### 1.1 End-to-End Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor Customer as Customer / POS Portal
    participant API as CafJourneyIngestionController
    participant Engine as GraphTraversalEngine
    participant SpEL as SpelEvaluator
    participant Bucket as BucketExecutionService
    participant Kafka as Kafka Broker (workflow-bucket-tasks)
    actor Ops as Operations / Manager
    participant Router as EventRoutingService
    participant Reg as IntegrationRegistry
    participant ExtUPSS as UPSS System (Age on Network)
    participant ExtOMS as OMS System (Order Creation)
    participant DB as Relational Database

    %% Step 1: Initiation
    Customer->>API: POST /api/workflows/caf-journey/execute (caf_number="CAF-2026-9901")
    API->>Engine: execute(workflowKey="caf-journey", businessKey="CAF-2026-9901")
    Engine->>DB: INSERT INTO workflow_instances (business_key='CAF-2026-9901', status='RUNNING')
    
    %% Step 2: Rule Node Evaluation
    Engine->>SpEL: evaluate(#context['idType'] == 'PASSPORT')
    SpEL-->>Engine: Returns true (Requires Manual KYC Review)
    
    %% Step 3: Bucket Suspension (KYC Review)
    Engine->>Bucket: Enqueue Bucket ("MANUAL_KYC_BUCKET")
    Bucket->>DB: INSERT INTO workflow_bucket_executions (status='PENDING')
    Bucket->>DB: INSERT INTO workflow_event_subscriptions (event_type='MANUAL_KYC_COMPLETED')
    Engine->>DB: UPDATE workflow_instances SET status='WAITING'
    Bucket->>Kafka: Publish BucketReadyEvent (topic: workflow-bucket-tasks)
    Engine-->>Engine: Virtual Thread Releases (Non-blocking)

    %% Step 4: Human Bucket Resolution
    Note over Ops: Ops agent reviews passport photo in Ops Portal
    Ops->>Router: POST /api/v1/buckets/executions/{id}/resolve {outcome: "APPROVED"}
    Router->>DB: UPDATE workflow_bucket_executions SET status='RESOLVED'
    Router->>DB: UPDATE workflow_event_subscriptions SET status='TRIGGERED'
    Router->>DB: UPDATE workflow_instances SET status='RUNNING'
    Router->>Engine: resume(instanceId, payload={bucketOutcome: "APPROVED"})

    %% Step 5: External Integration - UPSS Age on Network
    Engine->>Reg: Lookup "UPSS_AGE_ON_NETWORK_API"
    Reg-->>Engine: Returns endpoint https://upss.internal/api/v1/subscribers/9876543210
    Engine->>ExtUPSS: GET /subscribers/9876543210/profile
    ExtUPSS-->>Engine: HTTP 200 OK { ageOnNetworkDays: 450, status: "ACTIVE" }
    Engine->>Engine: Context updated (#context['ageOnNetworkDays'] = 450)

    %% Step 6: Decision Branch & Order Creation Command
    Engine->>SpEL: evaluate(#context['ageOnNetworkDays'] >= 90)
    SpEL-->>Engine: Returns true (Eligible for instant order creation)
    Engine->>Reg: Lookup "OMS_ORDER_CREATE_API"
    Reg-->>Engine: Returns endpoint https://oms.internal/api/v1/orders (POST)
    Engine->>ExtOMS: POST /orders { cafNumber: "CAF-2026-9901", msisdn: "9876543210" }
    ExtOMS-->>Engine: HTTP 201 Created { orderId: "ORD-883901" }
    Engine->>Engine: Context updated (#context['orderId'] = "ORD-883901")

    %% Step 7: Completion
    Engine->>DB: UPDATE workflow_instances SET status='COMPLETED'
    Engine-->>Customer: Journey Finished (Status: COMPLETED, Order ID: ORD-883901)
```

---

### 1.2 Role Breakdown of Engine Components Used in the Flow

| Component Name | Primary Role in the Journey | Key Database Table / Class |
| :--- | :--- | :--- |
| **`CafJourneyIngestionController`** | Entry point receiving the initial REST API submission containing `caf_number`. | Controller Class |
| **`WorkflowInstance`** | Stores runtime execution state, context variables (`#context`), and active node pointers. | `workflow_instances` |
| **`GraphTraversalEngine`** | Main execution orchestrator managing graph walking and node transitions. | `GraphTraversalEngine.java` |
| **`ActivationBasedEngine`** | Token propagation engine managing branch execution and parallel/join nodes. | `ActivationBasedEngine.java` |
| **`SpelEvaluator`** | Evaluates rules (`#context['idType'] == 'PASSPORT'`) and edge conditions securely using SpEL. | `SpelEvaluator.java` |
| **`BucketExecutionService`** | Creates and manages human review task queues (`MANUAL_KYC_BUCKET`). | `workflow_bucket_executions` |
| **`EventRoutingService`** | Correlates incoming resolution events (`caf_number`) and wakes up suspended instances. | `workflow_event_subscriptions` |
| **`IntegrationRegistry`** | Central catalog storing endpoint URLs, headers, and timeouts for UPSS and OMS APIs. | `workflow_integration_registry` |
| **`HttpRestCommand`** | Executes REST API requests to external systems (UPSS and OMS) and maps JSON responses. | `WorkflowCommand.java` |
| **`TaskRecorder`** | Records step-by-step audit logs for every node execution. | `workflow_task_instances` |

---

## 2. Relational Database Schema & Data Models

All tables use the `workflow_` prefix to enforce namespace isolation in enterprise shared relational databases (Oracle 19c / PostgreSQL).

```sql
-- 1. Metadata Root Table
CREATE TABLE workflow_definitions (
    workflow_definition_pk VARCHAR2(36) NOT NULL,
    wf_key VARCHAR2(100) NOT NULL,
    name VARCHAR2(255) NOT NULL,
    description VARCHAR2(1000),
    active_version NUMBER(10) DEFAULT 1 NOT NULL,
    circle_id NUMBER(10),
    active NUMBER(1) DEFAULT 1 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_workflow_def PRIMARY KEY (workflow_definition_pk),
    CONSTRAINT uk_workflow_def_key UNIQUE (wf_key)
);

-- 2. Immutable Canvas Graph Snapshots
CREATE TABLE workflow_versions (
    workflow_version_pk VARCHAR2(36) NOT NULL,
    workflow_definition_id VARCHAR2(36) NOT NULL,
    version NUMBER(10) NOT NULL,
    status VARCHAR2(20) NOT NULL, -- DRAFT, REVIEW, APPROVED, PUBLISHED, ARCHIVED
    definition_json CLOB NOT NULL, -- Serialized WorkflowGraphDto
    created_by VARCHAR2(100),
    updated_by VARCHAR2(100),
    circle_id NUMBER(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_workflow_ver PRIMARY KEY (workflow_version_pk),
    CONSTRAINT fk_wf_ver_def FOREIGN KEY (workflow_definition_id) 
        REFERENCES workflow_definitions(workflow_definition_pk) ON DELETE CASCADE,
    CONSTRAINT uk_wf_def_ver UNIQUE (workflow_definition_id, version)
);

-- 3. Stateful Runtime Execution Instances
CREATE TABLE workflow_instances (
    workflow_instance_pk VARCHAR2(36) NOT NULL,
    workflow_key VARCHAR2(100) NOT NULL,
    version_id VARCHAR2(36) NOT NULL,
    version_number NUMBER(10) NOT NULL,
    business_key VARCHAR2(100) NOT NULL, -- e.g. Domain CAF Number (CAF-2026-9901)
    status VARCHAR2(30) NOT NULL, -- CREATED, RUNNING, WAITING, COMPLETED, FAILED, TERMINATED
    current_node_id VARCHAR2(100),
    circle_id NUMBER(10),
    opt_lock_version NUMBER(19) DEFAULT 0 NOT NULL,
    serialized_context CLOB, -- JSON Map<String, Object>
    runtime_graph CLOB,     -- Token positions, active edge states
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_workflow_inst PRIMARY KEY (workflow_instance_pk),
    CONSTRAINT fk_wf_inst_ver FOREIGN KEY (version_id) 
        REFERENCES workflow_versions(workflow_version_pk)
);
CREATE INDEX idx_wf_inst_bizkey ON workflow_instances(business_key);
CREATE INDEX idx_wf_inst_status ON workflow_instances(status);

-- 4. Integration Registry Table (Decoupled Endpoint Configurations)
CREATE TABLE workflow_integration_registry (
    integration_pk VARCHAR2(36) NOT NULL,
    integration_key VARCHAR2(100) NOT NULL, -- e.g. UPSS_AGE_ON_NETWORK_API, OMS_ORDER_CREATE_API
    name VARCHAR2(200) NOT NULL,
    provider_type VARCHAR2(20) NOT NULL, -- REST, DB, CONFIG
    endpoint_url VARCHAR2(500),
    method VARCHAR2(10), -- GET, POST
    headers_json VARCHAR2(2000),
    request_template VARCHAR2(2000),
    timeout_ms NUMBER(10) DEFAULT 5000,
    circle_id NUMBER(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_wf_int_reg PRIMARY KEY (integration_pk),
    CONSTRAINT uk_wf_int_key UNIQUE (integration_key)
);

-- 5. Step-Level Audit Ledger
CREATE TABLE workflow_task_instances (
    task_instance_pk VARCHAR2(255) NOT NULL, -- {instance_id}_{nodeId}_{counter}
    instance_id VARCHAR2(36) NOT NULL,
    task_type VARCHAR2(50) NOT NULL,
    label VARCHAR2(200),
    status VARCHAR2(30) NOT NULL, -- WAITING, COMPLETED, FAILED, SKIPPED
    input_data CLOB,
    output_data CLOB,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT pk_wf_task_inst PRIMARY KEY (task_instance_pk),
    CONSTRAINT fk_wf_task_inst_parent FOREIGN KEY (instance_id) 
        REFERENCES workflow_instances(workflow_instance_pk) ON DELETE CASCADE
);

-- 6. Event Correlation Subscriptions
CREATE TABLE workflow_event_subscriptions (
    event_subscription_pk VARCHAR2(36) NOT NULL,
    instance_id VARCHAR2(36) NOT NULL,
    business_key VARCHAR2(100) NOT NULL, -- Correlated Business Key (e.g., caf_number)
    event_type VARCHAR2(100) NOT NULL,
    target_node_id VARCHAR2(100) NOT NULL,
    status VARCHAR2(30) DEFAULT 'ACTIVE' NOT NULL, -- ACTIVE, TRIGGERED, CANCELLED
    filter_attributes CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_wf_evt_sub PRIMARY KEY (event_subscription_pk),
    CONSTRAINT fk_wf_evt_sub_inst FOREIGN KEY (instance_id) 
        REFERENCES workflow_instances(workflow_instance_pk) ON DELETE CASCADE
);
CREATE INDEX idx_evt_sub_lookup ON workflow_event_subscriptions(business_key, event_type, status);

-- 7. Human Task Queues (Buckets)
CREATE TABLE workflow_buckets (
    bucket_id VARCHAR2(50) NOT NULL,
    name VARCHAR2(150) NOT NULL,
    description VARCHAR2(500),
    priority VARCHAR2(20) DEFAULT 'MEDIUM' NOT NULL,
    sla_hours NUMBER(10) DEFAULT 24 NOT NULL,
    owner_group VARCHAR2(100) NOT NULL,
    active NUMBER(1) DEFAULT 1 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_wf_buckets PRIMARY KEY (bucket_id)
);

CREATE TABLE workflow_bucket_executions (
    bucket_execution_pk VARCHAR2(36) NOT NULL,
    instance_id VARCHAR2(36) NOT NULL,
    bucket_id VARCHAR2(50) NOT NULL,
    status VARCHAR2(30) DEFAULT 'PENDING' NOT NULL, -- PENDING, IN_REVIEW, RESOLVED
    assigned_to VARCHAR2(100),
    resolution VARCHAR2(50),
    resolution_notes VARCHAR2(1000),
    resolved_by VARCHAR2(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT pk_wf_bucket_exec PRIMARY KEY (bucket_execution_pk),
    CONSTRAINT fk_wf_bexec_inst FOREIGN KEY (instance_id) 
        REFERENCES workflow_instances(workflow_instance_pk) ON DELETE CASCADE,
    CONSTRAINT fk_wf_bexec_bucket FOREIGN KEY (bucket_id) 
        REFERENCES workflow_buckets(bucket_id)
);

-- 8. Execution Telemetry Logs (Vertically Partitioned 1-to-1)
CREATE TABLE workflow_execution_logs (
    execution_log_pk VARCHAR2(36) NOT NULL,
    workflow_key VARCHAR2(100) NOT NULL,
    instance_id VARCHAR2(36) NOT NULL,
    status VARCHAR2(30) NOT NULL,
    step_count NUMBER(10) DEFAULT 0 NOT NULL,
    duration_ms NUMBER(19) DEFAULT 0 NOT NULL,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT pk_wf_exec_log PRIMARY KEY (execution_log_pk)
);

CREATE TABLE workflow_execution_log_details (
    log_id VARCHAR2(36) NOT NULL,
    input_context_json CLOB,
    execution_trace_json CLOB,
    CONSTRAINT pk_wf_exec_log_det PRIMARY KEY (log_id),
    CONSTRAINT fk_wf_exec_log_det_pk FOREIGN KEY (log_id) 
        REFERENCES workflow_execution_logs(execution_log_pk) ON DELETE CASCADE
);
```

---

## 3. External System Integration & Domain-Key Correlation

External systems interact using domain business keys (`businessKey` / `caf_number`, e.g., `CAF-2026-9901`). The engine maps external identifiers to internal execution instances via `workflow_event_subscriptions`, ensuring zero internal engine UUID leakage to external microservices.

---

## 4. Execution Engine Design & Traversal Mechanics

### 4.1 Activation-Based DAG Traversal Algorithm

```java
public class ActivationBasedEngine {

    public TraversalResult executeDag(CompiledWorkflowGraph graph, TraversalExecutionState state) {
        Map<String, Object> runtimeGraph = state.getRuntimeGraph();
        Set<String> activeTokens = state.getActiveTokens(); 
        Set<String> traversedEdges = state.getTraversedEdges();

        while (!activeTokens.isEmpty()) {
            // Step 1: Pop active token
            String currentNodeId = activeTokens.iterator().next();
            activeTokens.remove(currentNodeId);
            WorkflowNodeDto node = graph.getNodeMap().get(currentNodeId);

            // Step 2: Handle JOIN Synchronization (AND Gateway)
            if ("JOIN".equals(node.getType())) {
                List<WorkflowEdgeDto> incoming = graph.getIncomingEdges(currentNodeId);
                boolean allArrived = incoming.stream().allMatch(e -> traversedEdges.contains(e.getId()));
                if (!allArrived) continue; 
            }

            // Step 3: Execute node strategy & check for suspension
            NodeExecutor executor = nodeExecutorRegistry.get(node.getType());
            StepRecordDto step = state.createStepRecord(node);
            NodeExecutionResult result = executor.execute(node, state, step, state.getTrace());

            if (result.isSuspended()) {
                state.persistSuspensionState(currentNodeId);
                return new TraversalResult(state.getTrace(), true, currentNodeId, node.getLabel(), result.getOutcomeBucketId(), runtimeGraph);
            }

            // Step 4: Evaluate outgoing SpEL edge conditions
            List<WorkflowEdgeDto> outgoing = graph.getOutgoingEdges(currentNodeId);
            for (WorkflowEdgeDto edge : outgoing) {
                if (evaluateEdgeCondition(edge, graph, state.getContext())) {
                    traversedEdges.add(edge.getId());
                    activeTokens.add(edge.getTarget());
                    if ("DECISION".equals(node.getType())) break; 
                }
            }
        }
        return new TraversalResult(state.getTrace(), false, null, null, null, runtimeGraph);
    }
}
```

---

## 5. Bucket Movement & Task Queue Management

### 5.1 Parallel Bucket Execution Paths & Inactive Auto-Bypass

- **Parallel Buckets**: Parallel splits spawn independent `workflow_bucket_executions` and `workflow_event_subscriptions` rows. Tokens halt at `JOIN` nodes until all parallel bucket branches resolve.
- **Inactive Bucket Auto-Bypass**: If `workflow_buckets.active == 0`, `BucketNodeExecutor` skips creating workload rows, populates `#context['bucketOutcome'] = 'AUTO_APPROVED'`, logs step status as `SKIPPED`, and continues graph walking immediately.

---

## 6. SpEL Rules Engine & Security Hardening

SpEL expressions use `SimpleEvaluationContext` to block reflection (`T(java.lang.Runtime)...`) and limit scope to `#context`.

```java
public static SimpleEvaluationContext createEvaluationContext(Map<String, Object> context) {
    Map<String, Object> root = new HashMap<>(context);
    root.put("context", context);

    return SimpleEvaluationContext
            .forPropertyAccessors(new MapAccessor(), DataBindingPropertyAccessor.forReadOnlyAccess())
            .withRootObject(root)
            .build();
}
```

---

## 7. Integration Registry & Practical Example

`IntegrationRegistry` decouples HTTP URLs, authentication headers, and timeouts from canvas nodes. Practical integrations include fetching "Age on Network" from UPSS and dispatching `order_create` requests to OMS.

---

## 8. Error Handling, Retries & Fallback Strategy

Automated external calls (`COMMAND` nodes) can fail due to network timeouts, service degradation, or HTTP errors.

```mermaid
flowchart TD
    A[COMMAND Node Invocation] --> B[Execute External REST API]
    B -->|HTTP 200 OK| C[Map Response Payload to Context]
    B -->|HTTP 5xx / Timeout| D{Spring @Retryable Check}
    D -->|Retries < 3| E[Wait Backoff Delay 200ms * 1.5 & Retry]
    E --> B
    D -->|Max Retries Exceeded| F[Populate #context['error'] = 'TIMEOUT_EXCEEDED']
    F --> G{Canvas Has Fallback Edge?}
    G -->|Yes: #context['error'] != null| H[Route to Fallback Node / Fallback Bucket]
    G -->|No| I[Mark Instance Status = FAILED & Stop Traversal]
```

### 8.1 HTTP Command Retry & Timeout Rules
- **Default Timeout**: 5000ms (configurable per integration in `workflow_integration_registry`).
- **Retry Backoff**: `@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 200, multiplier = 1.5))`.
- **Fallback Edge Routing**: When a command fails after retries, the node populates `#context['error'] = 'SERVICE_UNAVAILABLE'`. If the node connects to an edge conditioned on `#context['error'] != null`, the engine routes execution to a manual fallback bucket rather than failing the workflow.

### 8.2 Kafka Event Consumer Dead Letter Queue (DLQ) Strategy
- Inbound event consumer topic: `workflow-events`.
- Retry Policy: 3 retry attempts with 1000ms backoff.
- **DLQ Recovery**: Unparseable or continuously failing events are published to `workflow-events-dlq` along with error diagnostics (`X-Exception-Message`, `X-Original-Topic`) for developer inspection.

---

## 9. Optimistic Concurrency Control & Self-Healing Retries

In high-throughput environments, multiple external callbacks or ops bucket resolutions may arrive simultaneously for the same `business_key`.

### 9.1 JPA Optimistic Locking (`opt_lock_version`)
All updates to `workflow_instances` evaluate the JPA `@Version` column (`opt_lock_version`). If Transaction A updates the instance context while Transaction B is committing, Transaction B receives an `OptimisticLockException`.

### 9.2 Self-Healing AOP Retry Handler
To prevent transient optimistic lock failures from returning errors to callers, `ExecutionService` and `EventRoutingService` wrap state updates in self-healing Spring retries:

```java
@Service
public class ExecutionService {

    @Retryable(
        retryFor = { ObjectOptimisticLockingFailureException.class, OptimisticLockException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2.0)
    )
    @Transactional
    public ExecutionLogDto resume(String instanceId, Map<String, Object> additionalContext) {
        // Automatically re-loads latest database state on lock collision and re-executes traversal
        WorkflowInstance instance = instanceRepository.findById(instanceId).orElseThrow();
        // Merge context & run traversal
        return processResume(instance, additionalContext);
    }
}
```

---

## 10. Engine Safeguards & Infinite Loop Circuit Breaker

To protect the JVM from infinite execution loops caused by cyclic/faulty canvas graphs (e.g. Node A $\rightarrow$ Node B $\rightarrow$ Node A), `GraphTraversalEngine` enforces a step threshold.

```java
public class GraphTraversalEngine {
    private static final int MAX_STEPS = 200; // Circuit breaker limit

    public TraversalResult traverse(...) {
        int stepCounter = 0;
        while (!activeTokens.isEmpty()) {
            stepCounter++;
            if (stepCounter > MAX_STEPS) {
                log.error("Circuit breaker triggered! Instance {} exceeded MAX_STEPS ({})", instanceId, MAX_STEPS);
                state.markFailed("INFINITE_LOOP_DETECTED: Exceeded maximum permitted step count of 200");
                return new TraversalResult(state.getTrace(), false, null, null, null, runtimeGraph);
            }
            // Continue normal node execution...
        }
    }
}
```

---

## 11. Data Dictionary & Context Schema Validation

To guarantee type safety across workflow steps, `workflow_context_schemas` and `workflow_context_fields` define data constraints for workflow variables.

### 1. Supported Data Types
- `STRING`, `NUMBER`, `BOOLEAN`, `DATE`, `OBJECT`.

### 2. Validation Execution at `START` Node
When a workflow starts, `StartNodeExecutor` evaluates incoming payload parameters against the context schema:

```java
public class ContextSchemaValidator {
    public void validate(Map<String, Object> inputContext, ContextSchema schema) {
        for (ContextField field : schema.getFields()) {
            if (field.getRequired() && !inputContext.containsKey(field.getFieldName())) {
                throw new IllegalArgumentException("Missing required context field: " + field.getFieldName());
            }
            if (inputContext.containsKey(field.getFieldName())) {
                Object value = inputContext.get(field.getFieldName());
                validateType(field.getFieldName(), value, field.getFieldType());
            }
        }
    }
}
```

---

## 12. Exhaustive REST API Specifications (OpenAPI Contract)

### 12.1 Workflow Execution & Instance APIs

#### 1. Execute / Start Workflow
- **Endpoint**: `POST /api/workflows/{workflowKey}/execute`
- **Request Body**:
```json
{
  "businessKey": "CAF-2026-9901",
  "circleId": 10,
  "initialContext": {
    "customerMsisdn": "9876543210",
    "idType": "PASSPORT"
  }
}
```
- **Response `200 OK`**:
```json
{
  "instanceId": "inst-884012",
  "workflowKey": "caf-journey",
  "versionNumber": 2,
  "status": "WAITING",
  "currentNodeId": "node-kyc-bucket",
  "currentNodeLabel": "Manual KYC Review Bucket"
}
```

#### 2. Resume Suspended Instance
- **Endpoint**: `POST /api/instances/{id}/resume`
- **Request Body**:
```json
{
  "bucketOutcome": "APPROVED",
  "reviewerNotes": "Biometric passport verified"
}
```
- **Response `200 OK`**:
```json
{
  "executionLogId": "log-99201",
  "status": "COMPLETED",
  "stepCount": 7,
  "durationMs": 420
}
```

---

### 12.2 Human Bucket Workload APIs

#### 1. List Bucket Workload Queue
- **Endpoint**: `GET /api/v1/buckets/executions?bucketId=MANUAL_KYC_BUCKET&status=PENDING`
- **Response `200 OK`**:
```json
{
  "content": [
    {
      "bucketExecutionPk": "bexec-9901",
      "instanceId": "inst-884012",
      "bucketId": "MANUAL_KYC_BUCKET",
      "status": "PENDING",
      "createdAt": "2026-09-28T14:30:00Z"
    }
  ],
  "totalElements": 1
}
```

#### 2. Resolve Bucket Workload Task
- **Endpoint**: `POST /api/v1/buckets/executions/{id}/resolve`
- **Request Body**:
```json
{
  "resolution": "APPROVED",
  "resolutionNotes": "Approved by Ops Lead",
  "resolvedBy": "OP_AGENT_44"
}
```
- **Response `200 OK`**:
```json
{
  "status": "RESOLVED",
  "bucketExecutionPk": "bexec-9901",
  "resolvedAt": "2026-09-28T14:35:12Z"
}
```

---

### 12.3 Audit & Step History APIs

#### 1. Get Instance Task Audit Steps
- **Endpoint**: `GET /api/instances/{id}/tasks`
- **Response `200 OK`**:
```json
[
  {
    "id": "inst-884012_start-node_1",
    "taskType": "START",
    "label": "Start Journey",
    "status": "COMPLETED",
    "inputData": "{ \"businessKey\": \"CAF-9901\" }",
    "outputData": "{ \"status\": \"INITIALIZED\" }",
    "startedAt": "2026-09-28T14:30:00Z",
    "completedAt": "2026-09-28T14:30:00Z"
  }
]
```

---

## 13. Summary & Developer Checklist

When implementing this engine, ensure the following core layers are verified:
1. **Schema Migrations**: Database DDLs for all 8 `workflow_*` tables with `@Version` lock columns.
2. **Graph Compilation**: Ahead-of-Time SpEL compilation (`SpelCompilerMode.IMMEDIATE`) in Caffeine L1 cache.
3. **Execution Engine**: Virtual Thread dispatching with token propagation (`ActivationBasedEngine`).
4. **Bucket Management**: Workload queue transitions (`PENDING` $\rightarrow$ `IN_REVIEW` $\rightarrow$ `RESOLVED`), parallel bucket JOIN convergence, and inactive bucket auto-bypass.
5. **Resilience & Security**: Security-hardened `SimpleEvaluationContext`, Spring `@Retryable` optimistic lock self-healing, `MAX_STEPS = 200` circuit breaker, and Kafka DLQ recovery.
