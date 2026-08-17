# Enterprise Workflow & Decision Subsystem — Functional D1 Summary

> **Document Type**: D1 (High-Level System Design & Architecture Specification)  
> **Subsystem Name**: Atlas Enterprise Workflow & Decision Subsystem  
> **Primary Focus**: Functional Capabilities ("WHAT the system does") with High-Level Architectural Insights ("HOW it works")  

---

## 1. Subsystem Overview

The **Atlas Enterprise Workflow & Decision Subsystem** is a metadata-configurable business process orchestration and decision engine. It enables business, operations, and IT teams to dynamically define, execute, monitor, and debug complex enterprise business flows (such as Customer Application Form [CAF] processing, SIM activations, KYC verifications, fraud checks, and approval chains) **entirely through configuration without software deployments or code releases**.

---

## 2. Core Functional Capabilities ("WHAT the Subsystem Provides")

### 2.1 Dynamic Visual Workflow Definition
- **Drag-and-Drop Canvas**: Business users and architects define process graphs visually using a drag-and-drop canvas (`atlas-ui`).
- **Rich Node Palette**: Supports decision branches, parallel splits, wait states, system commands, sub-workflows, and business buckets.
- **No-Code Rule Configuration**: Define decision rules and conditional pathing directly via visual property panels without writing application code.

### 2.2 Dynamic Bucket Management & Orchestration
- **Business Task Generation**: Dynamic generation of business activities (Buckets) such as A2 verification, OBCC calls, Fraud validation, and Manager approvals based on transaction metadata.
- **Bucket Lifecycle & SLAs**: Full management of bucket states (`CREATED`, `READY`, `IN_PROGRESS`, `COMPLETED`, `FAILED`), SLA timers, ownership groups, and escalation rules.
- **Dependency Chains**: Configurable bucket dependencies (e.g., *Police Verification can only begin after Premium Approval completes*).

### 2.3 Execution Replay & Time-Travel Debugging
- **Step-by-Step Historical Replay**: Replay any past transaction step-by-step to inspect exact execution path transitions, node inputs/outputs, and intermediate decision states.
- **Visual Execution Traces**: Highlights executed paths, active wait states, and branch outcomes directly on the visual workflow graph.
- **Root-Cause Analysis**: Quickly pinpoint failure reasons (e.g., API timeout, invalid customer attribute, or failed business rule) for operational support teams.

### 2.4 Decision Explainability & Operational Auditability
- **Transparent Decision Reasoning**: Answers critical business questions instantly:
  - *Why was a specific bucket created or skipped?*
  - *Which rule condition evaluated to TRUE?*
  - *Which external integration response triggered a failure?*
- **Immutable Audit Trail**: Captures a full, tamper-proof execution journal (`ExecutionLog`) for regulatory and operational compliance.

### 2.5 Safe Version Management & Zero-Downtime Releases
- **Draft & Publish Lifecycle**: Safely draft, validate, and test new workflow versions in isolation before publishing.
- **Running Instance Isolation**: Currently active transactions remain locked to the specific workflow version they started on, guaranteeing that new deployments never corrupt active customer journeys.
- **Instant Rollbacks**: Instantly revert to a previous workflow version with zero system downtime if an operational issue is detected.

### 2.6 Asynchronous Event-Driven Correlation & Integration
- **Long-Running Wait States**: Automatically suspends execution when awaiting external events (e.g. payment confirmations, third-party approvals, or manual back-office tasks).
- **Hybrid Event Correlation**: Inbound events (from Kafka, REST APIs, or Webhooks) are automatically matched to waiting workflow instances using business identifiers (e.g. `CAF_ID`, `MSISDN`).
- **Reusable Child Workflows (Call Activities)**: Trigger and embed isolated sub-workflows (e.g., standard KYC check) inside larger processes for high reusability.

---

## 3. High-Level System Architecture ("HOW It Works")

The subsystem achieves sub-millisecond execution and high throughput through four lightweight architectural layers:

```text
+-----------------------------------------------------------------------------------+
| 1. VISUAL DESIGNER & MANAGEMENT LAYER                                             |
|    - Web-based Drag-and-Drop Canvas (React + XYFlow)                              |
|    - Property panels for rules, bucket dependencies, and system integration tasks |
+-----------------------------------------+-----------------------------------------+
                                          | Publishes Workflow Metadata
                                          v
+-----------------------------------------------------------------------------------+
| 2. IN-MEMORY GRAPH CACHE & PRE-COMPILER                                           |
|    - Pre-indexes process graphs Ahead-Of-Time (AOT) for fast lookup                |
|    - Distributed Redis cluster caching for instant updates across server pods     |
+-----------------------------------------+-----------------------------------------+
                                          | Executes Zero-Allocation Traversal
                                          v
+-----------------------------------------------------------------------------------+
| 3. ENGINE TRAVERSAL & CONCURRENCY MANAGER                                         |
|    - GraphTraversalEngine: Evaluates decision paths, parallel splits, and joins   |
|    - Java 21 Virtual Threads: Non-blocking execution for external integrations    |
|    - Lazy Context Engine: Fetches customer data dynamically only when needed     |
+-----------------------------------------+-----------------------------------------+
                                          | Flushes State & Audit Logs
                                          v
+-----------------------------------------------------------------------------------+
| 4. PERSISTENCE & AUDIT ENGINE                                                     |
|    - Single-batch database persistence per transaction step (high efficiency)     |
|    - Execution Journal & Replay Engine for time-travel visual debugging           |
+-----------------------------------------------------------------------------------+
```

---

## 4. Key Summary Table for D1 Document

| Subsystem Feature | What Business Capability It Delivers | How It Operates Under the Hood |
| :--- | :--- | :--- |
| **Workflow Designer** | Visual creation and updating of business processes. | React + XYFlow canvas generating structured JSON graph metadata. |
| **Bucket Engine** | Creation and SLA tracking of manual or automated business tasks. | Dynamic rule evaluation triggering lifecycle task creation with SLA metrics. |
| **Decision Rules** | Flexible conditional logic without software releases. | Pre-compiled bytecode expression evaluation (AOT SpEL engine). |
| **Execution Replay** | Replaying past customer transactions for debugging and support. | Step-by-step reconstruction from execution journal snapshots. |
| **Explainability** | Full visibility into why decisions or routings occurred. | Context delta tracking and rule evaluation audit logging. |
| **Event Correlation** | Non-blocking execution awaiting external system notifications. | Subscription registry matching inbound payload attributes to suspended instances. |
| **Version Safety** | Zero-downtime updates with guaranteed safety for active transactions. | Version-pinned execution instances with instant pub/sub cache invalidation. |
