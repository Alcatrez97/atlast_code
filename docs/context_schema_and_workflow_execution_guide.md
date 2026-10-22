# Atlas Workflow Engine: Context Schema, Identifiers & Execution Guide

This document explains the core identity concepts (`contextId`, `businessKey`, `contextIdField`, and `targetPkColumn`), how they synchronize database domain entities in **Bucket Workflows**, and how to trigger workflows via **REST API** and **Apache Kafka**.

---

## 1. Core Identity Concepts & Mental Model

When developing workflows in Atlas, identifiers exist across three distinct layers:

| Concept | Layer | Definition | Concrete Example |
| :--- | :--- | :--- | :--- |
| **`contextIdField`** | Context Schema Definition | The schema configuration property telling the engine: *"Which key in the input context JSON map holds the primary business entity ID?"* | `"cafId"` or `"orderId"` |
| **`contextId`** | Workflow Runtime Context | The actual runtime value extracted from `contextIdField`. Used internally across task execution logs and domain sync services. | `"1004829"` |
| **`businessKey`** | State Machine Instance | The human-readable correlation key stored on the `WorkflowInstance` entity. External brokers (Kafka, MQ) and APIs use this key to correlate events and resume suspended instances. | `"1004829"` or `"CAF-1004829"` |
| **`targetTable` & `targetPkColumn`** | Relational Database Layer | The physical database table and primary key column that gets updated whenever the workflow enters or resolves a Bucket stage. | Table: `POSTPAID_ONBOARD_CAF`<br>PK Column: `caf_id` |

---

## 2. End-to-End Architecture & Lifecycle

The following diagram illustrates how the JSON payload maps to Context Schema, updates the database at Bucket stages, and resumes upon resolution:

```mermaid
sequenceDiagram
    autonumber
    actor Client as Client (REST or Kafka)
    participant Engine as Atlas ExecutionService
    participant CtxService as ContextSchemaService
    participant Sync as EntityStatusSyncService
    participant DB as Domain DB (e.g. POSTPAID_ONBOARD_CAF)
    participant BucketSvc as BucketResolutionService

    Client->>Engine: 1. Trigger workflow `a2-flow` with context { cafId: 1004829, ... }
    Engine->>CtxService: 2. Lookup ContextSchema for `a2-flow` (contextIdField = "cafId")
    Engine->>Engine: 3. Resolve contextId = "1004829" & businessKey = "1004829"
    Engine->>Engine: 4. Traverses state machine to BUCKET Node (e.g. "A2")
    Engine->>Sync: 5. Trigger syncStatus(workflowKey, contextId="1004829", status="A2 Pending")
    Sync->>DB: 6. UPDATE POSTPAID_ONBOARD_CAF SET form_status='A2 Pending' WHERE caf_id=1004829
    Engine-->>Client: 7. Workflow Instance Suspended in WAITING status

    Note over Client, BucketSvc: Approver reviews form or external system finishes validation...

    Client->>BucketSvc: 8. Resolve Bucket (businessKey="1004829", bucketId="A2", outcome="Accept")
    BucketSvc->>Sync: 9. UPDATE POSTPAID_ONBOARD_CAF SET form_status='A2 Approved' WHERE caf_id=1004829
    BucketSvc->>Engine: 10. Route resolution event & resume workflow traversal
```

---

## 3. ID Resolution & Fallback Ladder

When triggering an execution, **passing explicit `contextId` or `businessKey` parameters is optional**. The engine resolves them using this fallback order in `ExecutionService`:

### A. `contextId` Resolution
1. **Explicit Request Parameter**: `request.getContextId()`.
2. **Context Schema Property**: Looks up `context.get(schema.getContextIdField())` (e.g., `cafId`).
3. **Common Key Names**: Checks for keys `"contextId"`, `"cafId"`, or `"trackingId"` inside the context map.
4. **Business Key Fallback**: If `businessKey` was passed, `contextId` adopts that value.
5. **Auto-Generated Fallback**: Generates `UUID.randomUUID().toString()`.

### B. `businessKey` Resolution
1. **Explicit Request Parameter**: `request.getBusinessKey()`.
2. **Common Context Keys**: Checks for keys `"businessKey"`, `"cafId"`, `"transactionId"`, `"orderId"`, or `"trackingId"`.
3. **Context ID Fallback**: Falls back to the resolved `contextId`.
4. **Final Fallback**: `"BK-" + instanceId.substring(0, 8)`.

> [!TIP]
> If your Context Schema defines `contextIdField = "cafId"`, you only need to include `"cafId": 1004829` inside your `context` payload. The engine automatically populates both `contextId` and `businessKey` with `"1004829"`.

---

## 4. Triggering Workflows via REST API

### Endpoint Specification
* **Method:** `POST`
* **URL:** `http://<host>:9091/api/execute/{workflowKey}`
* **Header:** `Content-Type: application/json`

### Example: Triggering `a2-flow`
```bash
curl -X POST http://localhost:9091/api/execute/a2-flow \
  -H "Content-Type: application/json" \
  -d '{
    "context": {
      "cafId": 1004829,
      "circleId": 101,
      "customerName": "Alice Smith",
      "planName": "Postpaid Max",
      "simType": "ESIM",
      "channel": "RETAIL"
    }
  }'
```

### Sample Response (`201 Created` / `ExecutionLogDto`)
```json
{
  "id": "e931ca8f-fbb2-4a0b-9df0-b08e2f08a541",
  "workflowKey": "a2-flow",
  "versionNumber": 1,
  "instanceId": "c4d129fa-118c-4a30-8c87-897103dfb3a4",
  "businessKey": "1004829",
  "contextId": "1004829",
  "status": "WAITING",
  "outcomeNodeLabel": "A2 Verification",
  "steps": [
    {
      "stepOrder": 1,
      "nodeId": "start-node",
      "nodeLabel": "Start",
      "status": "COMPLETED"
    },
    {
      "stepOrder": 2,
      "nodeId": "bucket-a2",
      "nodeLabel": "A2 Verification",
      "status": "WAITING",
      "notes": "Suspended execution. Waiting on business outcome bucket: A2 Verification"
    }
  ]
}
```

---

## 5. Triggering Any Workflow via Apache Kafka

The Atlas engine includes a native Kafka consumer (`KafkaEventListener`) capable of dynamically triggering **any workflow** registered in the engine (e.g. `a2-flow`, `sim-swap`, `port-in`, `device-repair`, or any custom workflow).

### Kafka Topic Details
* **Default Topic Name:** `caf-lifecycle` (configurable via `kafka.topics.lifecycle` in `application.yml`)
* **Consumer Group ID:** `atlas-workflow-group`
* **Message Key:** `"1004829"` or your entity's unique key *(recommended: partition on `contextId` / `businessKey`)*

### 1. Generic Workflow Trigger Payload (Recommended for Any Workflow)
You can trigger **any** workflow by specifying `workflowKey` and using `contextId` or `businessKey`. Any arbitrary key-value pairs needed by the workflow nodes/conditions can be supplied inside `context`:

```json
{
  "workflowKey": "sim-swap-flow",
  "contextId": "SWAP-89231",
  "businessKey": "MSISDN-9876543210",
  "context": {
    "requestId": "SWAP-89231",
    "msisdn": "9876543210",
    "simType": "ESIM",
    "channel": "STORE",
    "agentId": "AGT-552"
  }
}
```

### 2. CAF-Specific Workflow Trigger Payload (`CafSubmittedEvent` Alias)
For telecom customer application form (CAF) workflows (such as `a2-flow`), you can use the domain-specific `cafId` field. The engine automatically maps `cafId` ↔ `contextId`:

```json
{
  "workflowKey": "a2-flow",
  "cafId": "1004829",
  "context": {
    "cafId": 1004829,
    "circleId": 101,
    "customerName": "Alice Smith",
    "planName": "Postpaid Max",
    "simType": "ESIM",
    "channel": "RETAIL"
  }
}
```

### How the Engine Executes Any Workflow from Kafka
When `KafkaEventListener` consumes a message:
1. **Dynamic Routing:** Reads `event.getWorkflowKey()`. If not provided, it defaults to `"order_processing"`.
2. **Context ID Resolution:** Resolves `contextId` from `event.getContextId() != null ? event.getContextId() : event.getCafId()`.
3. **Execution Service Call:** Dispatches directly to `executionService.execute(workflowKey, executionRequestDto)`.
4. **Context Schema & Primary Key Validation:** Validates the payload against the workflow definition's primary key and Context Schema rules.

### Kafka Producer CLI Example
```bash
# Example: Triggering a generic workflow
kafka-console-producer.sh --bootstrap-server localhost:9092 --topic caf-lifecycle \
  --property "parse.key=true" --property "key.separator=:"

SWAP-89231:{"workflowKey":"sim-swap-flow","contextId":"SWAP-89231","context":{"msisdn":"9876543210","channel":"STORE"}}

# Example: Triggering a CAF workflow
1004829:{"workflowKey":"a2-flow","cafId":"1004829","context":{"cafId":1004829,"circleId":101,"customerName":"Alice Smith"}}
```

---

## 6. Bucket Stage Suspensions & Domain Synchronization

When the workflow hits a BUCKET node (such as `A2`), `BucketSuspensionManager` executes:

1. **Creates a `BucketExecution` entity** in state `PENDING` with SLA and priority.
2. **Creates a `RevertStatus` / `EntityRevert` record** chaining to previous completed steps for audit and step-back capabilities.
3. **Synchronizes the Domain Database Table**:
   * Evaluates `DomainConventionService` against the schema.
   * If `a2-flow` overrides `targetTable` and `targetPkColumn`, it uses those.
   * Otherwise, it matches prefixes (e.g. `CAF_` $\rightarrow$ `POSTPAID_ONBOARD_CAF`, PK `caf_id`) or falls back to `POSTPAID_ONBOARD_CAF`.
   * Executes:
     ```sql
     UPDATE POSTPAID_ONBOARD_CAF 
     SET form_status = 'A2 Pending' 
     WHERE caf_id = 1004829;
     ```

---

## 7. Resolving Bucket Stages to Resume Workflow

Once manual verification, document check, or external evaluation is complete, the workflow is resumed by publishing a resolution signal.

### Option A: Via Kafka (`workflow-bucket-resolution` topic)
```json
{
  "businessKey": "1004829",
  "bucketId": "A2",
  "outcome": "Accept",
  "resolvedBy": "Agent_Bob",
  "resolutionNotes": "All documents verified and approved"
}
```

### Option B: Via REST API
```bash
POST http://localhost:9091/api/bucket-executions/{bucketExecutionId}/resolve
Content-Type: application/json

{
  "outcome": "Accept",
  "resolvedBy": "Agent_Bob",
  "notes": "All documents verified and approved"
}
```

### What Happens on Resolution:
1. `BucketExecution` record status changes from `PENDING` $\rightarrow$ `RESOLVED`.
2. Domain table status is updated:
   ```sql
   UPDATE POSTPAID_ONBOARD_CAF 
   SET form_status = 'A2 Approved' 
   WHERE caf_id = 1004829;
   ```
3. `EventRoutingService` correlates `businessKey = "1004829"`, unpauses the instance, and resumes traversal along the outbound edge matching outcome `"Accept"` to the next bucket or completion node.
