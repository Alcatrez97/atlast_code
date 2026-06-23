


_________________________________________________________________________________________

  +-------------+                  +-----------------+                  +-----------------+
  |             |  CAF_SUBMITTED   |                 |    BUCKET_READY  |                 |
  |  CAF System | ---------------> | Workflow Engine | ---------------> |  Kafka Topic /  |
  |             |  (Kafka Topic)   |                 |    (Kafka Topic) |  DLQ Processor  |
  +-------------+                  +-----------------+                  +-----------------+
                                            ^                                    |
                                            |                                    v
                                            |  BUCKET_COMPLETED         +-----------------+
                                            +-------------------------- | Bucket Consum. /|
                                              (Kafka Topic)             | Bucket UI Panel |
                                                                        +-----------------+


_____________________________________________________________________________________
                 [Engine]                                                     
                     │
                     ▼ (BucketReadyEvent)                                                    
 ┌────────────────────────────────────────┐                                                     
 │ 1. Kafka: "workflow-bucket-tasks"      │
 └──────────────────┬─────────────────────┘
                    │
                    ▼
 ┌────────────────────────────────────────┐
 │ 2. Bucket Approver Service             │  ◄── [Consumes and saves task to DB]
 └──────────────────┬─────────────────────┘
                    │
                    ▼ (Insert / Update)
 ┌────────────────────────────────────────┐
 │ 3. Database (Task/Inbox Table)         │
 └──────────────────┬─────────────────────┘
                    │
                    ▼ (Populates queue)
 ┌────────────────────────────────────────┐
 │ 4. React/Web UI (Operator Portal)      │  ◄── [Human logs in, views "A2 Queue"]
 └──────────────────┬─────────────────────┘
                    │                                                                                       
                    ▼ (Human clicks "Approve" / "Park")                                                     
 ┌────────────────────────────────────────┐                                                                 
 │ 5. Kafka: "workflow-bucket-resolution" │  ◄── [Dispatches decision back to Engine] ------------------------
 └────────────────────────────────────────┘










1. CAF Submission to Workflow (Asynchronous Event)
When a CAF is submitted, the CAF System updates its DB state to SUBMITTED and publishes a message to a Kafka topic named caf-lifecycle:

Event Type: CAF_SUBMITTED
Payload:
```json
{
  "cafId": "CAF-99211", // Map to workflow contextId
  "customerDetails": {
    "name": "Acme Corp",
    "amount": 250000,
    "region": "APAC"
  }
}
```


The Workflow Engine consumes this event, resolves the matching workflow definition (e.g. order_processing), and spawns a WorkflowInstance using the cafId as the contextId.

2. Manual Bucket Task Dispatch (Kafka + DLQ)
When the workflow engine traverses the graph and suspends at a BUCKET task node (e.g., bucketId: A2), it publishes a task dispatch event to a Kafka topic workflow-bucket-tasks:

Event Type: BUCKET_READY
Payload:
```json
{
  "instanceId": "487510d6-d1c6-48bc-a86d-6af6e9492cfc",
  "contextId": "CAF-99211",
  "bucketId": "A2",
  "priority": "HIGH",
  "slaHours": 24
}
```
Dead Letter Queue (DLQ): A DLQ is configured on this topic. If the consumer for a specific bucket UI goes down or throws database exceptions, the messages are routed to workflow-bucket-tasks-dlq for retry logic and alerting.
Bucket Subscription: Each bucket subsystem (Compliance team, Risk team, Servicing team) consumes this topic, filters by bucketId, and loads the task into their local review dashboard.
3. Resolving the 3 States (Approve, Reject, Park)
Once the reviewer makes a decision in their local dashboard, the bucket subsystem publishes a resolution event to workflow-bucket-resolution:

Event Type: BUCKET_COMPLETED
Payload:
```json
{
  "instanceId": "487510d6-d1c6-48bc-a86d-6af6e9492cfc",
  "bucketId": "A2",
  "outcome": "Park", // Accept, Reject, or Park
  "resolvedBy": "reviewer_john",
  "notes": "Waiting for customer document verification"
}
```
4. State Machine Routing Configuration
The workflow engine consumes the BUCKET_COMPLETED event, updates the instance context form_status accordingly, and resumes execution.

You configure your workflow graph downstream using a Decision Node with three edges mapping directly to the three outcomes:

Downstream Edge   SpEL Condition Expression  Target Action / Destination
Approve  context.form_status == 'A2Accept'   Routes to succeeding steps (e.g. Dispatch Node)
Reject   context.form_status == 'A2Reject'   Routes to a rejection termination or rollback steps
Park  context.form_status == 'A2Park'  Routes to a WAIT/TIMER node to delay, or loops back to retry verification later