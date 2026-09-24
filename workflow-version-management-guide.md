# Atlas Workflow Engine: Version Management Guide

This document details the architectural design, lifecycle governance, database persistence, and runtime execution isolation for workflow versioning within the Atlas State Machine & Workflow Engine.

---

## 1. Architectural Overview & Data Model

Atlas separates top-level workflow catalog declarations from their specific versioned implementations using a **Header-to-Snapshot** design pattern.

- **Workflow Definition (Header)**: Represents the logical workflow identity (e.g., `POSTPAID_EKYC`), its tenant/circle affiliation, and points to the currently active production version (`active_version`).
- **Workflow Version (Snapshot)**: An immutable capture of the canvas graph (nodes, edges, expressions, visual coordinates) along with governance metadata (`status`, `author`, `timestamps`).

### Entity Relationship Model

```mermaid
erDiagram
    workflow_definitions ||--o{ workflow_versions : "has 1..N versions"
    workflow_versions ||--o{ workflow_instances : "pins execution"
    workflow_versions ||--o{ workflow_execution_logs : "records audit trace"

    workflow_definitions {
        varchar(36) workflow_definition_pk PK
        varchar(100) wf_key UK "Unique logical workflow key"
        varchar(255) name
        int active_version "Points to currently active version number"
        int circle_id
        boolean active
        timestamp created_at
        timestamp updated_at
    }

    workflow_versions {
        varchar(36) workflow_version_pk PK
        varchar(36) workflow_definition_id FK
        int version "Sequential version number (1, 2, 3...)"
        varchar(20) status "DRAFT, REVIEW, APPROVED, PUBLISHED, ARCHIVED"
        clob definition_json "Serialized canvas graph DTO"
        varchar(100) created_by
        varchar(100) updated_by
        int circle_id
        timestamp created_at
        timestamp updated_at
    }

    workflow_instances {
        varchar(36) workflow_instance_pk PK
        varchar(100) workflow_key
        varchar(36) version_id FK "Pinned version UUID"
        int version_number "Pinned version number"
        varchar(30) status "RUNNING, WAITING, COMPLETED, FAILED"
        varchar(100) business_key
    }
```

---

## 2. Version Lifecycle & Governance State Machine

Workflow definitions adhere to an audited four-stage lifecycle designed to prevent unvalidated changes from impacting production execution.

```mermaid
stateDiagram-v2
    [*] --> DRAFT: Auto-created (v1) or Draft Branched (vN)
    
    state "DRAFT (Mutable)" as DRAFT {
        [*] --> CanvasEditing
        CanvasEditing --> CanvasSaved: PUT /api/workflows/versions/{id}
    }
    
    DRAFT --> REVIEW: Transition to REVIEW (Graph Validation Triggered)
    
    state "REVIEW (Read-Only)" as REVIEW {
        [*] --> PeerOrLeadReview
    }
    
    REVIEW --> APPROVED: Approver Approves
    REVIEW --> DRAFT: Rejected (Sent back for edits)
    
    state "APPROVED (Read-Only)" as APPROVED {
        [*] --> StagedForRelease
    }
    
    APPROVED --> PUBLISHED: Activation (Sets active_version on Definition)
    APPROVED --> DRAFT: Reverted
    
    state "PUBLISHED (Active Production)" as PUBLISHED {
        [*] --> L1CacheWarmed
        L1CacheWarmed --> NewExecutionsRouted
    }
    
    PUBLISHED --> ARCHIVED: Soft Deletion (When replaced or decommissioned)
    DRAFT --> [*]: Hard Delete (Allowed only if 0 instances exist)
    ARCHIVED --> [*]
```

### Lifecycle Gate Rules

| Status | Mutability | Allowed Transitions | Triggered Actions / Validations |
| :--- | :--- | :--- | :--- |
| **`DRAFT`** | **Mutable** | `REVIEW`, (Hard Delete) | Canvas graph can be updated. Cannot be executed in production. |
| **`REVIEW`** | **Immutable** | `APPROVED`, `DRAFT` | Executes `validateWorkflowGraph()`: verifies all wait events match registered Kafka schemas, command configurations, and bucket outcomes. |
| **`APPROVED`** | **Immutable** | `PUBLISHED`, `DRAFT` | Staged for production rollout. |
| **`PUBLISHED`** | **Immutable** | `ARCHIVED` | Updates `workflow_definitions.active_version`. Pre-warms AOT SpEL bytecode and $O(1)$ adjacency graph cache in Caffeine L1 cache. |
| **`ARCHIVED`** | **Immutable** | None | Retained for auditability of historical and long-running instances. |

> [!IMPORTANT]
> **No Direct Publish**: Transitioning directly from `DRAFT` to `PUBLISHED` is strictly blocked by the engine. All changes must pass through `REVIEW` and `APPROVED` gates.

---

## 3. Version Creation & Auto-Cloning Strategy

When engineers modify or evolve an existing production workflow:
1. The engine looks up the highest assigned version number:
   $$\text{nextVersion} = \max(\text{version}) + 1$$
2. A new `WorkflowVersion` is generated in `DRAFT` status.
3. **Deep-Cloning**: If the author does not provide a blank graph, the engine deep-clones the canvas topology (all nodes, coordinates, decision expressions, and connecting edges) from the previous version. The engineer can then iterate incrementally without recreating nodes.

```mermaid
sequenceDiagram
    autonumber
    actor Author
    participant Controller as WorkflowController
    participant Service as WorkflowService
    participant Repo as WorkflowVersionRepository
    participant DB as Oracle Database

    Author->>Controller: POST /api/workflows/{defId}/versions
    Controller->>Service: createDraftVersion(defId, optionalGraph)
    Service->>Repo: findMaxVersionByDefinitionId(defId)
    Repo-->>Service: maxVersion = 2
    Service->>Repo: findByWorkflowDefinitionIdAndVersion(defId, 2)
    Repo-->>Service: Latest Version 2 Snapshot
    Service->>Service: cloneGraph(v2.definition)
    Service->>DB: INSERT INTO workflow_versions (v3, 'DRAFT', cloned_graph)
    Service-->>Author: WorkflowVersionDto (v3, DRAFT)
```

---

## 4. In-Flight Execution Isolation & Version Pinning

Enterprise workflows in Atlas often span days or weeks (e.g., waiting for manual document verification, biometric approval buckets, or external callbacks). 

When a new workflow version is published, **in-flight instances must continue on their original version** to prevent structural inconsistencies, missing state nodes, or schema mismatches.

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Engine as ExecutionService
    participant InstanceRepo as WorkflowInstanceRepository
    participant Traversal as GraphTraversalEngine
    participant DB as Database

    Note over Client, DB: Step 1: Request arrives while Version 1 is Active
    Client->>Engine: POST /api/workflows/POSTPAID_EKYC/execute
    Engine->>DB: Query active_version for POSTPAID_EKYC (Returns v1)
    Engine->>InstanceRepo: Save WorkflowInstance(id="INST-100", version_id="V1-UUID", version_number=1)
    Engine->>Traversal: Execute graph against V1-UUID
    Traversal-->>InstanceRepo: Instance pauses at "WAIT_A2_APPROVAL"

    Note over Client, DB: Step 2: Version 2 is Published by Workflow Author
    Client->>Engine: Transition v2 to PUBLISHED
    Engine->>DB: UPDATE workflow_definitions SET active_version = 2

    Note over Client, DB: Step 3: New Execution starts on Version 2
    Client->>Engine: POST /api/workflows/POSTPAID_EKYC/execute
    Engine->>DB: Query active_version for POSTPAID_EKYC (Returns v2)
    Engine->>InstanceRepo: Save WorkflowInstance(id="INST-200", version_id="V2-UUID", version_number=2)

    Note over Client, DB: Step 4: INST-100 Resumes (Pinned to Version 1)
    Client->>Engine: PUT /api/forms/884012/status ("TOO_A2Accept")
    Engine->>InstanceRepo: Load INST-100 (version_id="V1-UUID")
    Engine->>Traversal: Resume execution using V1-UUID compiled graph
    Note over Traversal: INST-100 safely finishes on Version 1 without corruption!
```

---

## 5. AOT Graph Compilation & L1 Caffeine Caching

Atlas uses `WorkflowGraphCompiler` to eliminate graph interpretation overhead during high-concurrency execution:

1. **Ahead-of-Time SpEL Compilation**: All rule node expressions and conditional edge predicates are parsed and compiled into JVM bytecode via Spring's `SpelCompilerMode.IMMEDIATE`.
2. **$O(1)$ Adjacency Lookups**: Topologically pre-indexes incoming/outgoing edges and node maps into immutable memory structures.
3. **Cluster Invalidation & Pre-Warming**:
   - Updating a draft automatically invalidates any stale compiled graph in the local Caffeine L1 cache.
   - Promoting a version to `PUBLISHED` triggers **ahead-of-time pre-warming**, ensuring that the very first live execution has sub-millisecond dispatch latency.

```mermaid
flowchart LR
    A[WorkflowVersion.definition_json] --> B[WorkflowGraphCompiler]
    B --> C[SpEL Bytecode Compilation]
    B --> D[O(1) Adjacency Indexing]
    C --> E[CompiledWorkflowGraph]
    D --> E
    E --> F[(Caffeine L1 Cache)]
    F --> G[GraphTraversalEngine Runtime Execution]
```

---

## 6. Deletion Protection & Historical Archiving

To maintain strict regulatory compliance and audit trails:

- **Active Version Protection**: A `PUBLISHED` version cannot be deleted.
- **Referential Integrity Protection**:
  - The engine checks `workflowInstanceRepository.countByWorkflowVersionId(versionId)`.
  - If instances exist ($\text{count} > 0$), the version **cannot be hard-deleted**. It is automatically transitioned to `ARCHIVED`.
  - Only unexecuted `DRAFT` versions with 0 historical instances can be purged from the database.

---

## 7. Version Management REST API Reference

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/workflows/{id}` | Retrieves workflow definition and list of all its version summaries. |
| `POST` | `/api/workflows/{id}/versions` | Branches a new `DRAFT` version (`maxVersion + 1`), auto-cloning graph from the latest version. |
| `GET` | `/api/workflows/versions/{versionId}` | Retrieves complete graph topology and layout details for a version. |
| `PUT` | `/api/workflows/versions/{versionId}` | Saves/updates canvas graph (Allowed only when `status == 'DRAFT'`). |
| `PUT` | `/api/workflows/versions/{versionId}/status` | Transitions version through governance gates (`DRAFT` $\rightarrow$ `REVIEW` $\rightarrow$ `APPROVED` $\rightarrow$ `PUBLISHED`). |
| `GET` | `/api/workflows/active/{workflowKey}` | Fetches currently published version for execution inspection. |
| `DELETE` | `/api/workflows/versions/{versionId}` | Hard-deletes unused drafts or archives versions that have historical executions. |
