# External Approval & Bucket Task Integration Guide

> **Target Audience**: Integration Developers, External System Engineers, Backend Engineers, Solution Architects  
> **Subsystem**: Atlas Enterprise Workflow & Decision Platform  
> **Document Purpose**: Complete end-to-end integration reference for external approval systems, back-office portals, 3rd-party risk engines, and Kafka event consumers.  

---

## 1. Executive Summary & Integration Architecture

When a workflow execution reaches a **Bucket Task** (e.g., `PREMIUM_NUMBER_APPROVAL`, `FRAUD_VERIFICATION`, `POLICE_VERIFICATION`) or a **Wait Event** node, the Atlas Workflow Engine performs the following non-blocking operations:

1. **Registers an Event Subscription**: Atlas saves an active wait-state record in the database (`workflow_event_subscriptions`).
2. **Emits an Outbound Notification**: Atlas publishes an event to Kafka (topic: `workflow-bucket-tasks`) containing task details and business identifiers.
3. **Suspends Traversal (`WAITING`)**: The workflow engine releases database connection locks and parks execution until the external approval is completed.

When the external approval system finishes processing, it emits an **Inbound Event** (via Kafka topic `workflow-events` or REST API `POST /api/v1/events/route`). Atlas matches the event, updates task state to `COMPLETED`, maps approval outputs into the runtime context, and resumes graph traversal.

```text
====================================================================================================
                                      OUTBOUND INTEGRATION FLOW
====================================================================================================
   Atlas Workflow Engine                 Kafka Broker                    External Approval System
   (GraphTraversalEngine)           (topic: workflow-bucket-tasks)          (Portal / Risk Service)
             |                                    |                                    |
             | 1. Enters BUCKET node              |                                    |
             | 2. Saves EventSubscription         |                                    |
             | 3. Emits BucketReadyEvent -------->|                                    |
             |    (eventId, businessKey, etc.)    | 4. Consumes Outbound Event ------->|
             | 5. Suspends Traversal (WAITING)   |                                    | 6. Creates Local
             |                                    |                                    |    Approval Ticket
             v                                    v                                    v

====================================================================================================
                                       INBOUND INTEGRATION FLOW
====================================================================================================
   Atlas Workflow Engine                 Kafka Broker                    External Approval System
   (EventRoutingService)               (topic: workflow-events)                 (Manager Action)
             |                                    |                                    |
             |                                    |                                    | 7. Manager Approves
             |                                    |<------- 8. Publishes Inbound Event |    Ticket
             | 9. Consumes Inbound Event          |            (businessKey, status)   |
             | 10. Correlates EventSubscription   |                                    |
             | 11. Maps Payload into Context      |                                    |
             | 12. Resumes Traversal (COMPLETED)  |                                    |
             v                                    v                                    v
```

---

## 2. Integration Modes (Choosing How to Correlate Events)

Atlas supports two integration modes for external callback correlation.

### Mode A: Domain Business Key Correlation (Recommended / Loosely Coupled)
- **Concept**: The external system **only stores domain identifiers** (e.g. `CAF_ID` or `MSISDN`) and **does not store engine UUIDs**.
- **How It Works**: The inbound completion message contains `businessKey: "CAF100892"` and `eventType: "PREMIUM_APPROVAL_COMPLETED"`. Atlas queries `workflow_event_subscriptions` by `business_key` + `event_type`, resolves the internal `instanceId` automatically, and resumes traversal.
- **Best Use Case**: Third-party vendors, external legacy portals, mobile apps, or partner systems.

### Mode B: Direct Instance ID Correlation (Tightly Coupled)
- **Concept**: The external system persists Atlas internal `instanceId` alongside its ticket.
- **How It Works**: The inbound message includes `instanceId: "WF_inst_99812"`. Atlas directly fetches the instance by primary key.
- **Best Use Case**: High-speed internal microservices tightly integrated with Atlas.

---

## 3. Outbound Integration Guide (Consuming Tasks from Atlas)

### 3.1 Outbound Kafka Topic
- **Default Topic**: `workflow-bucket-tasks` (Configurable via `kafka.topics.bucket-tasks`)
- **Key**: `bucketId` (e.g. `PREMIUM_APPROVAL`)

### 3.2 Outbound Event Schema (`BucketReadyEvent`)

Every outbound event published by Atlas contains the following JSON structure:

```json
{
  "eventId": "evt_a8912bc0-3f11-4209-b42e-110293817261",
  "instanceId": "WF_inst_99812",
  "contextId": "CAF100892",
  "businessKey": "CAF100892",
  "bucketId": "PREMIUM_APPROVAL",
  "bucketName": "Premium Number Approval Queue",
  "priority": "HIGH",
  "slaHours": 24,
  "circleId": 11,
  "dependencyBucketIds": ["A2_VERIFICATION"]
}
```

### 3.3 Outbound Field Dictionary

| Field Name | Type | Description |
| :--- | :--- | :--- |
| `eventId` | `String` (UUID) | **Unique Message Identifier**: Generated per Kafka dispatch. Used by external systems to perform message deduplication and prevent duplicate task creation. |
| `instanceId` | `String` | **Atlas Workflow Instance ID**: Internal engine primary key for the running workflow execution. |
| `contextId` / `businessKey` | `String` | **Domain Business Identifier**: The primary domain key (e.g. `CAF_ID`, `MSISDN`, `ORDER_ID`). |
| `bucketId` | `String` | **Bucket Identifier**: Business queue code (e.g., `PREMIUM_APPROVAL`, `OBCC`, `FRAUD_CHECK`). |
| `bucketName` | `String` | **Display Name**: Human-readable name of the bucket queue. |
| `priority` | `String` | **Task Priority**: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`. |
| `slaHours` | `Integer` | **SLA Threshold**: Time allowed in hours before SLA breach. |
| `circleId` | `Integer` | **Telecom Circle Identifier**: Telecom circle/region code (e.g., 11 = MH, 12 = DL). |
| `dependencyBucketIds` | `Array[String]` | **Pre-requisite Buckets**: List of bucket IDs that must complete prior to this task. |

### 3.4 External System Deduplication Logic
To guarantee exactly-once task creation on the external portal side, external consumers **MUST** implement deduplication using `eventId`:

```java
// Example External Consumer Logic
public void onBucketTaskReceived(BucketReadyEvent event) {
    if (redis.hasKey("processed_event:" + event.getEventId())) {
        log.info("Duplicate event ignored: {}", event.getEventId());
        return; // Skip duplicate
    }
    
    // Save task ticket in external database
    approvalTicketRepository.createTicket(event.getBusinessKey(), event.getBucketId());
    
    // Mark event processed for 24 hours
    redis.set("processed_event:" + event.getEventId(), "1", 24, TimeUnit.HOURS);
}
```

---

## 4. Inbound Integration Guide (Sending Approvals Back to Atlas)

When the external approval processing is complete (Approved or Rejected), the external system notifies Atlas.

### 4.1 Inbound Communication Channels

Systems can return callbacks via **Kafka** (preferred) or **HTTP REST API**.

#### Option A: Inbound Kafka Topic (Preferred)
- **Topic**: `workflow-events` (Configurable via `kafka.topics.events`)
- **Consumer Group**: `atlas-workflow-group`

#### Option B: HTTP REST Endpoint (Fallback for non-Kafka systems)
- **Endpoint**: `POST /api/v1/events/route`
- **Headers**: `Content-Type: application/json`

---

### 4.2 Inbound Payload Schemas

#### Schema A: Approval Success Event Payload

```json
{
  "eventType": "PREMIUM_APPROVAL_COMPLETED",
  "businessKey": "CAF100892",
  "payload": {
    "status": "APPROVED",
    "approvedBy": "mgr_john",
    "approvalNotes": "SIM cost discount verified",
    "discountAmount": 500
  }
}
```

#### Schema B: Approval Rejection Event Payload

```json
{
  "eventType": "PREMIUM_APPROVAL_COMPLETED",
  "businessKey": "CAF100892",
  "payload": {
    "status": "REJECTED",
    "approvedBy": "mgr_john",
    "rejectionReason": "Credit score below threshold"
  }
}
```

### 4.3 Inbound Field Dictionary

| Field Name | Required | Type | Description |
| :--- | :--- | :--- | :--- |
| `eventType` | **Yes** | `String` | Matches the `eventType` defined on the node in the visual workflow graph (e.g. `PREMIUM_APPROVAL_COMPLETED`). |
| `businessKey` | **Yes** (Mode A) | `String` | Domain business key (e.g., `CAF100892`) used to match active subscriptions in Atlas. |
| `instanceId` | **Yes** (Mode B) | `String` | Atlas workflow instance ID (if using Mode B direct lookup). |
| `payload` | **Yes** | `Object` | Key-value object containing approval attributes. The attributes are automatically mapped into the workflow runtime context. |

---

## 5. Visual Canvas Configuration Guide (`atlas-ui`)

To configure a node for external approval in the visual workflow designer:

```text
 ┌────────────────────────────────────────────────────────┐
 │ Node Configuration Drawer                              │
 ├────────────────────────────────────────────────────────┤
 │ Node Type:    [ BUCKET / WAIT_EVENT  ▼ ]              │
 │ Node Label:   [ Premium Number Approval ]              │
 │ Bucket ID:    [ PREMIUM_APPROVAL       ]               │
 │ Event Type:   [ PREMIUM_APPROVAL_COMPLETED ]           │
 ├────────────────────────────────────────────────────────┤
 │ Payload Mapping (Optional Field Extraction):           │
 │  - Source Attribute: [ status        ] -> [ appStatus ] │
 │  - Source Attribute: [ approvedBy    ] -> [ approver  ] │
 └────────────────────────────────────────────────────────┘
```

1. **Drag Node**: Drag a `BUCKET` or `WAIT_EVENT` node onto the canvas.
2. **Set Bucket ID**: Enter the queue identifier (e.g. `PREMIUM_APPROVAL`).
3. **Set Event Type**: Enter the inbound event key (e.g. `PREMIUM_APPROVAL_COMPLETED`).
4. **Configure Payload Mapping** (Optional): Map incoming Kafka payload fields to specific context variables (e.g., map `payload.status` to `context['appStatus']`).
5. **Connect Outgoing Conditional Edges**:
   - **Approved Edge**: SpEL Condition: `#context['appStatus'] == 'APPROVED'` $\rightarrow$ Connect to Activation Node.
   - **Rejected Edge**: SpEL Condition: `#context['appStatus'] == 'REJECTED'` $\rightarrow$ Connect to Rejection/Notification Node.

---

## 5.1 Downstream Accessibility of Inbound Event Data

**YES! All data received in the inbound event payload is automatically saved into the runtime `context` and is 100% available to all downstream nodes in the workflow.**

When the inbound event arrives, `ExecutionService.resume()` passes the event payload to `ResumeRouter.applyPayloadMapping()`, which merges the attributes into the workflow's shared execution `context`.

### Downstream Access Patterns:

1. **Downstream Decision & Rule Nodes (SpEL Expressions)**:
   Downstream rules and edge conditions evaluate mapped or raw payload fields directly:
   ```text
   #context['appStatus'] == 'APPROVED'
   #context['discountAmount'] > 500
   #context['approvedBy'] != null
   ```

2. **Downstream Command & REST Nodes (`COMMAND` / `HTTP_REST`)**:
   Downstream system dispatches (such as updating CRM, calling provision APIs, or publishing Kafka notifications) reference inbound approval variables directly in their parameter templates:
   ```json
   {
     "cafId": "#context['businessKey']",
     "formStatus": "#context['appStatus']",
     "approver": "#context['approvedBy']"
   }
   ```

3. **Downstream Child Workflows & Sub-Tasks (`SUB_WORKFLOW` / `BUCKET`)**:
   Subsequent buckets or child sub-workflows inherit the updated context map, passing approver names, timestamps, and notes to downstream task workers.

4. **Live Debugger & Operational Audit Trail**:
   The inbound event payload is saved in `TaskInstance.outputData` and `WorkflowInstance.serializedContext`, enabling visual inspection of callback data in `atlas-ui`.

---

## 6. Engine Spring Boot Configuration (`application.yml`)

Ensure the following properties are set in `atlas-workflow-service`:

```yaml
kafka:
  enabled: true
  bootstrap-servers: localhost:9092
  group-id: atlas-workflow-group
  topics:
    events: workflow-events
    bucket-tasks: workflow-bucket-tasks

spring:
  kafka:
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

---

## 7. Complete End-to-End Walkthrough Example

### Scenario: Telecom Prepaid-to-Postpaid SIM Migration (`CAF100892`)

1. **Step 1: Atlas Traverses Node**:
   - CAF `CAF100892` enters node `PREMIUM_APPROVAL`.
   - Engine creates `EventSubscription(businessKey="CAF100892", eventType="PREMIUM_APPROVAL_COMPLETED")`.
   - Instance state becomes `WAITING`.

2. **Step 2: Atlas Emits Outbound Kafka Event**:
   - Topic: `workflow-bucket-tasks`
   - Payload:
     ```json
     {
       "eventId": "evt_99812-abc",
       "instanceId": "WF_INST_7712",
       "businessKey": "CAF100892",
       "bucketId": "PREMIUM_APPROVAL",
       "priority": "HIGH"
     }
     ```

3. **Step 3: External Portal Consumes Event**:
   - Portal receives `evt_99812-abc`, checks deduplication cache, and displays ticket for `CAF100892` on the Manager Dashboard.

4. **Step 4: Manager Approves Ticket**:
   - Manager clicks **Approve** on the portal.

5. **Step 5: External Portal Emits Inbound Kafka Event**:
   - Topic: `workflow-events`
   - Payload:
     ```json
     {
       "eventType": "PREMIUM_APPROVAL_COMPLETED",
       "businessKey": "CAF100892",
       "payload": {
         "status": "APPROVED",
         "approvedBy": "manager_alice"
       }
     }
     ```

6. **Step 6: Atlas Correlates & Resumes Workflow**:
   - `KafkaEventConsumer` receives event $\rightarrow$ passes to `EventRoutingService.routeEvent()`.
   - `EventRoutingService` matches `businessKey="CAF100892"` + `eventType="PREMIUM_APPROVAL_COMPLETED"`.
   - Updates task state to `COMPLETED`, maps `appStatus = "APPROVED"` into context, and resumes traversal to the next node.

---

## 8. Verification & Troubleshooting Checklist

| Issue | Root Cause | Solution |
| :--- | :--- | :--- |
| **Inbound Event Warning: "No active event subscriptions found"** | `businessKey` or `eventType` mismatch between external event and registered node subscription. | Check active records in DB table `workflow_event_subscriptions` where `business_key = 'CAF100892'`. Verify case-sensitivity of `eventType`. |
| **Duplicate Approval Tasks Created on Portal** | External system is not performing deduplication. | Use `eventId` from `BucketReadyEvent` as the deduplication key in Redis/DB before creating portal tickets. |
| **Kafka Message Not Consumed** | `kafka.enabled` set to `false`. | Ensure `kafka.enabled=true` in `application.yml`. |
| **Payload Attributes Not Appearing in Context** | Payload key names differ from SpEL rules or `payloadMapping` is misconfigured. | Inspect `workflow_instances.serialized_context` or view execution traces in `atlas-ui` live debugger. |
