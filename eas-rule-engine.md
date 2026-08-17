# Enterprise Workflow Orchestration & Decision Platform

# Engineering Architecture Specification (EAS)

# Part 1 – Vision, Principles, Core Architecture and Foundational Design Decisions

Version: 1.0

Author: Architecture Specification

Status: Draft

---

# 1. Executive Summary

This document describes the architecture of a configurable enterprise-grade workflow orchestration and decision platform designed to support highly dynamic telecom business processes without requiring application code changes for most business rule modifications.

The primary business driver is the ability to dynamically control Customer Application Form (CAF) processing flows through configuration rather than software deployments.

Examples include:

* Dynamic A2 bucket creation
* Dynamic Premium Number Approval routing
* Dynamic Police Verification routing
* Dynamic OBCC bucket generation
* Dynamic Fraud Verification routing
* Dynamic KYC validation paths
* Dynamic Prepaid to Postpaid journeys
* Future business scenarios not known during initial development

The platform must enable business and operations teams to:

* Create and modify workflow logic
* Define bucket creation criteria
* Define decision logic
* Configure dependencies between buckets
* Reconfigure execution paths
* Reuse existing integrations
* Deploy workflow changes without software releases

while simultaneously ensuring:

* High performance
* High scalability
* Explainability
* Traceability
* Auditability
* Operational debugging
* Version safety

---

# 2. Problem Statement

## 2.1 Existing Enterprise Problem

In traditional telecom systems:

```text
CAF Submitted
      |
      v
Java Service
      |
      v
Hardcoded If Else Logic
      |
      v
Create Buckets
      |
      v
Update Status
```

Business logic is embedded in Java code.

Example:

```java
if(kycType.equals("DKYC")){
   createA2Bucket();
}

if(simCost > 5000){
   createPremiumApprovalBucket();
}

if(customerType.equals("FOREIGNER")){
   createPoliceVerificationBucket();
}
```

Problems:

* Every change requires deployment.
* Every new bucket requires development effort.
* Business users cannot configure behavior.
* Multiple systems become tightly coupled.
* Debugging becomes difficult.
* Logic becomes duplicated across services.

---

## 2.2 Target State

Business rules become metadata.

Example:

```text
Rule:
If KYC Type = DKYC
Then Create A2 Bucket
```

Configured via UI.

No code change required.

Workflow engine interprets configuration dynamically.

---

# 3. Key Architectural Insight

This platform is NOT primarily:

* A Rule Engine
* A BPM Engine
* A State Machine

It is a combination of all three.

---

## 3.1 Why This Is Not Just Drools

Drools assumes:

```text
All Facts Available
      |
      v
Evaluate Rules
```

Our problem:

```text
Need Data
      |
Need Rules
      |
Need Workflow
      |
Need Bucket Lifecycle
```

Facts are not always available.

Some facts require:

* REST calls
* SOAP calls
* MQ interactions
* Database queries
* Configuration lookups
* Derived calculations

Therefore:

Drools alone cannot be the primary engine.

---

## 3.2 Why This Is Not Just a State Machine

Traditional state machine:

```text
A -> B -> C -> D
```

Our business flow:

```text
Submitted
     |
     +------> A2
     |
     +------> OBCC
     |
     +------> Police Verification
     |
     +------> Premium Approval
```

Required buckets are dynamically generated.

The graph changes per transaction.

Therefore:

Pure state machine is insufficient.

---

## 3.3 Why This Is Not Just BPM

Traditional BPM assumes:

```text
Process Flow
```

Our system must also support:

```text
Data Acquisition
Decision Evaluation
Bucket Generation
Dynamic Routing
```

which are beyond normal BPM concerns.

---

# 4. Architectural Principles

The following principles govern the entire system.

---

## Principle 1

Configuration over Code

Business behavior must be configurable.

New bucket introduction should not require software deployment.

---

## Principle 2

Context First

Rules must never directly invoke integrations.

Rules consume context.

Context acquisition happens separately.

---

## Principle 3

Lazy Data Acquisition

Data should only be fetched when required.

Never preload all data.

---

## Principle 4

Single Data Fetch

A variable should only be fetched once per execution.

Subsequent access must use cache.

---

## Principle 5

Explainability

Every decision must be explainable.

System must answer:

```text
Why was bucket created?
Why was bucket skipped?
Why was transaction rejected?
```

---

## Principle 6

Version Safety

Running transactions must never be impacted by newly deployed workflows.

---

## Principle 7

Replayability

Every execution must be reproducible.

---

## Principle 8

Integration Independence

Workflow definitions must never contain:

* SOAP payloads
* XML templates
* MQ definitions
* Protocol details

Those belong to integration definitions.

---

## Principle 9

Ahead-Of-Time (AOT) Bytecode Compilation

Rule expressions, edge conditions, and payload mappings must be compiled Ahead-Of-Time into native JVM bytecode (using `SpelCompilerMode.IMMEDIATE`) to eliminate reflection overhead during active graph traversal.

---

## Principle 10

Zero-Allocation Graph Traversal

In-memory compiled workflow graphs (`CompiledWorkflowGraph`) must maintain pre-indexed topological maps ($O(1)$ node and edge lookup) to eliminate heap allocations per traversal step and maximize throughput under concurrent load.

---

## Principle 11

Deferred In-Memory Batch Task Persistence

Task state transitions during traversal must be accumulated in-memory (`TraversalContext.inMemoryTasks`) with $0\text{ ms}$ RAM read-your-own-writes lookups and flushed to the database in a single batch (`flushPendingTasks`) at transaction boundaries, eliminating HikariCP connection locking bottlenecks.

---

## Principle 12

Non-Blocking Virtual Thread Execution

All network-bound external I/O operations (`isExternalIo()`) and asynchronous command executions must be offloaded to Java 21 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`), propagating traversal context seamlessly via `InheritableThreadLocal<TraversalContext>`.

---

## Principle 13

Optimistic Locking & Self-Healing Resumption

Workflow instance states must be protected using optimistic locking (`@Version optLockVersion`). Race conditions during concurrent asynchronous event resumptions must self-heal using `@Retryable` backoff without dropping events or throwing 500 errors.

---

# 5. Core Domain Concepts

---

## CAF

Customer Application Form.

Primary business object.

Example:

```json
{
  "cafId":"CAF123",
  "msisdn":"98xxxxxxx",
  "customerType":"PREPAID"
}
```

---

## TaskInstance (Contract-Based Work Unit)

Represents any granular unit of work within a workflow execution, replacing rigid bucket-only models. A task can be:

* **Synchronous**: Evaluated immediately (e.g. SpEL rules, variable mappings).
* **Asynchronous**: Suspends traversal, persists state, and registers event subscriptions.

Statuses: `RUNNING`, `COMPLETED`, `FAILED`, `SUSPENDED`.

---

## Bucket

A business activity requiring completion (legacy work unit model preserved for backward compatibility).

Examples:

* A2
* OBCC
* Police Verification
* Premium Approval
* Fraud Verification

---

## EventSubscription

A declaration that a workflow instance is waiting for a correlated inbound event.

Correlation formula:

$$\text{Correlation Key} = \text{Business Key} + \text{Event Type} + \text{Filter Attributes}$$

---

## Context

Runtime information used for decisions.

Examples:

```text
Age On Network
ARPU
Customer Type
Circle
Dealer Category
Fraud Score
commandOutputs.<nodeId>.<param>
```

---

## Workflow & CompiledWorkflowGraph

`Workflow`: Graph definition describing execution flow.

`CompiledWorkflowGraph`: In-memory immutable pre-compiled graph holding direct $O(1)$ topological lookup maps for nodes, incoming edges, outgoing edges, and pre-compiled SpEL bytecode.

---

## Rule & SpelEvaluator

Condition determining business outcome, evaluated natively via bytecode-compiled Spring Expression Language ASTs.

---

## Variable

Named value stored in execution context.

Example:

```text
ageOnNetwork
```

---

## Provider

Mechanism used to retrieve variable value.

Examples:

```text
REST
SOAP
DB
MQ
CONFIG
```

---

# 6. High-Level Architecture

```text
                        +--------------------+
                        |  React + XYFlow    |
                        |   (atlas-ui Canvas)|
                        +---------+----------+
                                  |
                                  v
                  +--------------------------------+
                  |  Workflow Management Layer     |
                  |  (WorkflowService / Rest API)  |
                  +---------------+----------------+
                                  |
                                  v
                  +--------------------------------+
                  |  Multi-Level Graph Cache       |
                  |  - L1: JVM Caffeine Cache      |
                  |  - L2: Redis Pub/Sub Sync      |
                  +---------------+----------------+
                                  | Zero-Allocation O(1) Lookup
                                  v
                  +--------------------------------+
                  |  Graph Traversal Engine        |
                  |  - CompiledWorkflowGraph       |
                  |  - AOT SpEL Bytecode Evaluator |
                  |  - Java 21 Virtual Threads     |
                  +---------------+----------------+
                                  |
                  +---------------+---------------+
                  |                               |
                  v                               v
  +-------------------------------+   +-------------------------------+
  | Context Resolution Engine     |   | Deferred Task Persistence     |
  | - LazyContextMap              |   | - TaskRecorder In-Memory      |
  | - Rest / Db ContextProviders  |   | - Single Batch DB Flush       |
  +---------------+---------------+   +---------------+---------------+
                  |                               |
                  v                               v
        +---------+--------+             +--------+-------+
        | REST / SOAP / DB |             | H2 / Postgres  |
        | Integrations     |             | Persistence    |
        +------------------+             +----------------+
```

---

# 7. Major Platform Components

---

## Workflow Designer

Purpose:

Configure business flow visually.

Technology:

* React
* XYFlow

Responsibilities:

* Node creation (`START`, `END`, `DECISION`, `COMMAND`, `PARALLEL`, `JOIN`, `WAIT_EVENT`, `BUCKET`, `SUB_WORKFLOW`)
* Edge creation & condition mapping
* Visual validation
* Version management & live execution debugging

---

## Workflow Graph Compiler & Multi-Level Cache

Purpose:

Compile definitions Ahead-Of-Time into bytecode and serve zero-allocation graph instances.

Technology:

* Caffeine (L1 In-Memory Cache)
* Redis Pub/Sub (L2 Distributed Invalidation)
* `SpelCompilerMode.IMMEDIATE`

Responsibilities:

* Pre-indexing topological nodes and edges
* AOT bytecode compilation of rules and edge conditions
* Automatic cache warming on publishing
* Cluster-wide cache invalidation via Redis Pub/Sub

---

## Execution Engine (`GraphTraversalEngine`)

Purpose:

Execute compiled workflow graphs across concurrent execution frontiers.

Responsibilities:

* Parallel branch fan-out (`PARALLEL`) and converging wait states (`JOIN`)
* Asynchronous execution state suspension
* Rule evaluation via bytecode SpEL
* Task lifecycle recording

---

## Virtual Thread Execution Manager

Purpose:

Provide lightweight non-blocking execution concurrency.

Technology:

* Java 21 Project Loom (`Executors.newVirtualThreadPerTaskExecutor()`)
* `InheritableThreadLocal<TraversalContext>` (`TraversalContextHolder`)

Responsibilities:

* Offloading slow external I/O (`isExternalIo()`) from carrier threads
* Background execution of ASYNC `COMMAND` nodes via post-commit transaction callbacks
* Propagating traversal context transparently across thread boundaries

---

## TaskRecorder & Deferred Persistence Engine

Purpose:

Eliminate per-step database SQL roundtrips during graph traversal.

Responsibilities:

* In-memory accumulation of `TaskInstance` transitions inside `TraversalContext`
* Read-your-own-writes $0\text{ ms}$ RAM resolution for downstream nodes
* Single batch database flush (`flushPendingTasks`) upon traversal pause or completion

---

## Context Engine

Purpose:

Provide required variables dynamically.

Responsibilities:

* Dependency analysis
* Lazy loading via `LazyContextMap`
* Variable resolution via `RestContextProvider` and `DbContextProvider`
* Namespaced command outputs (`commandOutputs.<nodeId>.<param>`)

---

## Audit Engine & Execution Journal

Purpose:

Persist execution history and structured step details.

Responsibilities:

* Journaling (`ExecutionLog`, `TaskInstance`)
* State replayability
* Operational investigation

---

## Debugger

Purpose:

Transaction-level visualization.

Responsibilities:

* Node-by-node execution timeline
* Rule traces & SpEL evaluation outputs
* Context snapshot traces
* API call traceses

---

# 8. Why Context Catalog Exists

One of the most important architectural decisions.

Without Context Catalog:

Workflow definitions become tightly coupled to integrations.

Example:

```text
Rule
   |
Call CRM API
   |
Parse XML
   |
Evaluate
```

Problems:

* Repetition
* Complexity
* Maintenance nightmare

---

Instead:

```text
Rule
   |
AgeOnNetwork
```

Context Catalog maps:

```text
AgeOnNetwork
      |
CRM API
```

Benefits:

* Reuse
* Simplicity
* Centralized maintenance
* Better debugging

---

# 9. Why Lazy Resolution Was Chosen

Naive approach:

```text
Fetch Everything
Then Evaluate
```

Problem:

Most data may never be needed.

Example:

```text
PREPAID Path
```

requires:

```text
AgeOnNetwork
ARPU
```

POSTPAID path requires:

```text
CreditScore
BillingHistory
```

If transaction enters PREPAID path:

```text
CreditScore
BillingHistory
```

fetches become wasted.

---

Chosen design:

```text
Evaluate Condition
       |
Need Variable?
       |
Fetch Variable
```

Benefits:

* Lower latency
* Lower API load
* Lower DB load
* Better scalability

---

# 10. Why Execution Journal Was Chosen

Traditional logs:

```text
INFO
INFO
INFO
```

are difficult to debug.

Instead:

Structured execution journal.

Example:

```json
{
  "event":"RULE_EVALUATED",
  "rule":"OBCC_RULE",
  "result":true
}
```

Benefits:

* Replay
* Visualization
* Root Cause Analysis
* Audit Compliance

---

# End of Part 1

Next document will cover:

Part 2:

* Context Catalog Architecture
* Variable Resolution Engine
* Dependency Graphs
* Lazy Loading Framework
* Provider Framework
* REST/SOAP/MQ Integration Architecture
* Context Caching Strategy
* Derived Variables
* Expression Engine
* Execution Planning Engine


# Enterprise Workflow Orchestration & Decision Platform

# Engineering Architecture Specification (EAS)

# Part 2 – Context Catalog, Variable Resolution Engine, Integration Framework, Dependency Analysis and Execution Planning

Version: 1.0

Author: Architecture Specification

Status: Draft

---

# 11. Context Resolution Architecture

## 11.1 Introduction

The Context Resolution Engine is the most critical subsystem of the platform.

While the Workflow Engine controls process flow, the Context Engine determines:

```text
What data is needed?
When should it be fetched?
How should it be fetched?
How often should it be fetched?
Can it be reused?
Can it be cached?
```

The overall scalability of the platform depends on the quality of the Context Resolution Engine.

---

## 11.2 Why This Component Exists

Naive workflow engines often execute:

```text
Workflow
    |
Fetch Everything
    |
Evaluate Rules
```

This creates several problems:

### Problem 1

Unnecessary API calls

```text
PREPAID branch selected

POSTPAID variables fetched anyway
```

---

### Problem 2

Duplicate API calls

```text
Rule A uses AgeOnNetwork

Rule B uses AgeOnNetwork

Rule C uses AgeOnNetwork
```

Result:

```text
3 CRM calls
```

instead of:

```text
1 CRM call
```

---

### Problem 3

Slow execution

Large telecom integrations may take:

```text
300ms
500ms
2000ms
```

per API call.

Unnecessary calls significantly increase latency.

---

## 11.3 Selected Design

Chosen architecture:

```text
Workflow
      |
Dependency Analysis
      |
Execution Planning
      |
Lazy Context Resolution
      |
Rule Evaluation
```

---

# 12. Context Catalog

## 12.1 Purpose

Context Catalog acts as a central registry for all variables available within the platform.

The workflow designer should never know:

* REST endpoints
* SOAP services
* MQ queues
* Database tables

Instead they work with business variables.

---

## 12.2 Example

Business User Sees

```text
Age On Network
Customer Segment
ARPU
Fraud Score
Dealer Category
```

Not:

```text
http://crm/api/customer/profile
```

---

## 12.3 Context Variable Definition

Example:

```json
{
  "variableId":"AGE_ON_NETWORK",
  "displayName":"Age On Network",
  "dataType":"INTEGER",
  "provider":"CRM_AON_PROVIDER",
  "cacheable":true,
  "lazy":true
}
```

---

## 12.4 Supported Data Types

```text
STRING
INTEGER
LONG
DOUBLE
BOOLEAN
DATE
TIMESTAMP
LIST
MAP
OBJECT
ENUM
```

---

## 12.5 Metadata

Every variable must contain metadata.

```json
{
  "variableId":"AGE_ON_NETWORK",
  "description":"Customer age on prepaid network",
  "owner":"CRM Team",
  "criticality":"HIGH",
  "cacheable":true,
  "ttl":300
}
```

---

# 13. Variable Categories

## 13.1 Static Variables

Loaded from configuration.

Example:

```text
OBCC_THRESHOLD
```

---

## 13.2 Runtime Variables

Derived from incoming CAF.

Example:

```text
CAF_ID
MSISDN
SIM_TYPE
```

---

## 13.3 Integration Variables

Retrieved through providers.

Example:

```text
ARPU
AON
FRAUD_SCORE
```

---

## 13.4 Derived Variables

Calculated from other variables.

Example:

```text
LOYAL_CUSTOMER
```

Formula:

```text
AGE_ON_NETWORK > 180
AND
ARPU > 250
```

---

# 14. Context Provider Framework

## 14.1 Overview

Context Providers retrieve data.

The workflow engine never directly calls:

* APIs
* Databases
* MQ

Instead:

```text
Workflow
     |
Variable
     |
Provider
```

---

## 14.2 Provider Interface

Example:

```java
public interface ContextProvider {

    ProviderResult resolve(
          ContextRequest request);
}
```

---

## 14.3 Provider Types

### REST Provider

Used for:

```text
Microservices
External APIs
Internal APIs
```

---

### SOAP Provider

Used for:

```text
Legacy Telecom Systems
CRM Systems
Billing Platforms
```

---

### Database Provider

Used for:

```text
Oracle
PostgreSQL
MySQL
DB2
```

---

### MQ Provider

Used for:

```text
IBM MQ
RabbitMQ
Kafka Request Reply
```

---

### Cache Provider

Used for:

```text
Redis
Memcached
```

---

### File Provider

Used for:

```text
Property Files
CSV
Static Configurations
```

---

### Custom Java Provider

Used when standard providers are insufficient.

---

# 15. Integration Registry

## 15.1 Purpose

A workflow should never contain integration implementation details.

Bad Example:

```text
Call CRM URL
Build XML
Parse Response
```

inside workflow.

---

Chosen Architecture:

```text
Workflow
      |
Context Variable
      |
Integration Registry
      |
Provider
```

---

## 15.2 Example

```json
{
  "integrationId":"CRM_AON",
  "providerType":"SOAP",
  "executor":"crmAonExecutor"
}
```

---

## 15.3 Benefits

### Reusability

Multiple workflows reuse same integration.

---

### Central Maintenance

API changes in one place.

---

### Security

Credentials not exposed to workflow users.

---

# 16. Large Payload Handling

## 16.1 Problem

Enterprise APIs frequently require:

* Large XML payloads
* Complex JSON payloads
* Dynamic enrichment
* Multiple lookups

Example:

```text
Activation Request
```

may require:

```text
Customer Data
Dealer Data
Circle Data
Product Data
CAF Data
```

before request generation.

---

## 16.2 Why UI-Based Payload Builders Were Rejected

Although technically possible, they become unmaintainable.

Problems:

```text
Large XML Structures
Namespace Management
Transformations
Conditional Elements
Encryption
Signatures
```

cannot be effectively modeled through drag-and-drop screens.

---

## 16.3 Selected Approach

Developers own integrations.

Business users consume variables.

---

Example:

Developer builds:

```text
CRM_AON_PROVIDER
```

Business uses:

```text
AgeOnNetwork
```

---

# 17. Response Mapping Framework

## 17.1 Purpose

Extract values from provider responses.

---

Example Response

```json
{
  "customer":{
      "ageOnNetwork":220
  }
}
```

---

Mapping

```json
{
  "variable":"AGE_ON_NETWORK",
  "path":"customer.ageOnNetwork"
}
```

---

Context Result

```json
{
  "AGE_ON_NETWORK":220
}
```

---

## 17.2 Supported Formats

### JSON Path

```text
customer.ageOnNetwork
```

---

### XML XPath

```text
/customer/profile/aon
```

---

### Custom Extractor

```java
AgeOnNetworkExtractor
```

---

# 18. Lazy Context Resolution

## 18.1 Core Principle

Variables should only be fetched when required.

---

Example

Workflow:

```text
Check Customer Type
         |
PREPAID ?
    |
YES
    |
Check AON
```

Execution:

```text
Resolve CustomerType

CustomerType = POSTPAID

Stop
```

AgeOnNetwork never fetched.

---

## 18.2 Lazy Variable Proxy

Internally:

```java
context.get("AGE_ON_NETWORK");
```

triggers provider execution.

---

## 18.3 Benefits

### Reduced API Load

### Reduced Database Load

### Faster Processing

### Better Scalability

---

# 19. Context Cache

## 19.1 Problem

Same variable used multiple times.

Example:

```text
Rule A

Rule B

Rule C
```

All use:

```text
AGE_ON_NETWORK
```

---

## 19.2 Selected Design

First access:

```text
Provider Call
```

Subsequent accesses:

```text
Context Cache
```

---

## 19.3 Cache Levels

### Transaction Cache

Valid only during execution.

---

### Node Cache

Valid during node execution.

---

### Distributed Cache

Optional Redis layer.

---

# 20. Derived Variables

## 20.1 Purpose

Business concepts should not be duplicated.

Bad:

```text
AON > 180 AND ARPU > 250
```

repeated 50 times.

---

## 20.2 Example

```json
{
  "variable":"LOYAL_CUSTOMER",
  "expression":
     "AGE_ON_NETWORK > 180 &&
      ARPU > 250"
}
```

---

Usage:

```text
LOYAL_CUSTOMER == TRUE
```

---

## 20.3 Benefits

### Reusability

### Readability

### Maintainability

---

# 21. Dependency Graph Engine

## 21.1 Purpose

Determine variable dependencies.

---

Example

```text
LOYAL_CUSTOMER
      |
      +----> AGE_ON_NETWORK
      |
      +----> ARPU
```

---

## 21.2 Dependency Tree

Generated at workflow compilation time.

---

Benefits:

### Detect Missing Variables

### Optimize Resolution

### Enable Explainability

---

# 22. Execution Planning Engine

## 22.1 Purpose

Generate optimized execution plan.

---

Instead of:

```text
Evaluate Everything
```

Build:

```text
Step 1

Resolve CustomerType

Step 2

Evaluate Branch

Step 3

Resolve Branch Variables

Step 4

Evaluate Rules
```

---

## 22.2 Why It Exists

Without planning:

```text
Huge Number Of Unnecessary Calls
```

---

With planning:

```text
Only Required Variables Resolved
```

---

# 23. Cost-Based Optimization

## 23.1 Problem

Not all variables cost the same.

---

Examples

```text
CustomerType
```

Cost:

```text
LOW
```

---

```text
AgeOnNetwork
```

Cost:

```text
HIGH
```

---

## 23.2 Variable Metadata

```json
{
  "variable":"AGE_ON_NETWORK",
  "cost":"HIGH"
}
```

---

## 23.3 Optimization Strategy

Evaluate:

```text
LOW COST CONDITIONS
```

before

```text
HIGH COST CONDITIONS
```

---

Example

```text
CustomerType == PREPAID
AND
AgeOnNetwork > 180
```

Evaluate:

```text
CustomerType
```

first.

If false:

```text
Skip AgeOnNetwork
```

No API call.

---

# 20. SpEL & AOT Bytecode Expression Engine

## 20.1 Ahead-Of-Time (AOT) Bytecode Compilation (`SpelCompilerMode.IMMEDIATE`)

To achieve sub-millisecond rule evaluation latency, expression string parsing and reflection-based evaluation are eliminated.

When a workflow version is published or loaded, `WorkflowGraphCompiler` and `SpelEvaluator` initialize a `SpelParserConfiguration` configured with `SpelCompilerMode.IMMEDIATE`.

```java
SpelParserConfiguration config = new SpelParserConfiguration(
    SpelCompilerMode.IMMEDIATE, 
    this.getClass().getClassLoader()
);
SpelExpressionParser parser = new SpelExpressionParser(config);
```

During the first evaluation pass, SpEL parses the expression AST. On subsequent evaluations, SpEL automatically compiles the AST into **native JVM bytecode instructions**, reducing evaluation latency from $\sim 0.8\text{ ms}$ down to $\sim 0.05\text{ ms}$ ($\sim 15\times$ performance gain).

---

## 20.2 Command Output Namespacing (`commandOutputs.<nodeId>.<param>`)

When `COMMAND` nodes (e.g. `UPDATE_FORM_STATUS`, `HTTP_REST`, `MQ_PUBLISH`) complete execution synchronously or asynchronously, their outputs are mapped into the shared `TraversalContext` under a standardized object path:

```json
{
  "cafId": "CAF123",
  "customerType": "PREPAID",
  "commandOutputs": {
    "cmd-async-task-1": {
      "status": "APPROVED",
      "processedAt": "2026-08-17T10:15:30",
      "responseCode": 200
    }
  }
}
```

Downstream SpEL expressions reference command outputs cleanly:

```text
#commandOutputs['cmd-async-task-1']['status'] == 'APPROVED'
```

---

## 20.3 Non-Blocking Virtual Thread External I/O Offloading (`isExternalIo()`)

When variable providers or `COMMAND` nodes perform remote external dispatches (HTTP REST, Kafka MQ, SOAP), `WorkflowCommand.isExternalIo()` flags the operation.

The engine offloads remote network dispatches to the dedicated Java 21 `workflowVirtualTaskExecutor` (`Executors.newVirtualThreadPerTaskExecutor()`).

Benefits:

* Database connections (HikariCP pool) are released back to the pool before engaging in long network roundtrips.
* OS carrier threads are freed while virtual threads park on socket read/write calls.
* Request-level timeouts guarantee bounded latency.

---

# 24. Why This Design Was Chosen

Alternative Design:

```text
Fetch Entire Context First
```

Rejected because:

* High latency
* Poor scalability
* Wasted API calls
* Expensive integrations

---

Alternative Design:

```text
Rules Directly Call APIs
```

Rejected because:

* Tight coupling
* Poor observability
* Duplicate calls
* Hard debugging

---

Selected Design:

```text
Workflow
      |
Execution Plan
      |
Lazy Context Resolution (LazyContextMap)
      |
Provider Cache
      |
AOT Bytecode SpEL Evaluation
      |
Namespaced Context Propagation
```

because it provides:

* Performance
* Scalability
* Explainability
* Maintainability
* Reusability

---

# End of Part 2

Next Document:

Part 3 – Workflow Engine, Bucket Engine, State Machine Runtime, Rule Evaluation Framework, Dynamic Bucket Generation, Dependency Chains, Versioning, Transaction Lifecycle and Runtime Execution Model.

# Enterprise Workflow Orchestration & Decision Platform

# Engineering Architecture Specification (EAS)

# Part 3 – Workflow Engine, Bucket Engine, State Machine Runtime, Rule Evaluation Framework, Dynamic Bucket Generation, Dependency Chains, Versioning and Transaction Lifecycle

Version: 1.0

Author: Architecture Specification

Status: Draft

---

# 25. Workflow Runtime Philosophy

## 25.1 Fundamental Design Principle

The platform is **not a traditional state machine**.

Traditional state machines assume:

```text
A -> B -> C -> D
```

Our business problem is fundamentally different.

For every CAF, the execution path is generated dynamically.

Example:

```text
CAF Submitted
      |
      v
Determine Required Buckets
      |
      +------> A2
      |
      +------> OBCC
      |
      +------> Premium Approval
      |
      +------> Police Verification
      |
      +------> Fraud Verification
```

The actual path depends on:

* Customer type
* KYC type
* SIM cost
* Circle
* Dealer category
* Age on network
* Future business requirements

which are unknown during system design.

---

## 25.2 Core Runtime Concept

Runtime execution consists of three phases.

```text
Phase 1
Context Acquisition

Phase 2
Decision Evaluation

Phase 3
Bucket Orchestration
```

---

# 26. Workflow Definition Model

## 26.1 Purpose

Workflow Definition represents a business process template.

Example:

```text
PREPAID ACTIVATION
POSTPAID ACTIVATION
PRE2POST MIGRATION
NUMBER PORTABILITY
SIM REPLACEMENT
```

---

## 26.2 Workflow Structure

```text
Workflow
     |
     +---- Nodes
     |
     +---- Edges
     |
     +---- Rules
     |
     +---- Bucket Definitions
```

---

## 26.3 Workflow Metadata

Example:

```json
{
  "workflowId":"PRE2POST",
  "version":"12",
  "status":"PUBLISHED",
  "description":"Prepaid to Postpaid migration workflow"
}
```

---

# 27. Node Architecture & Compiled Topology

## 27.1 `CompiledWorkflowGraph` & $O(1)$ Topological Maps

To eliminate dynamic map allocations and graph indexing overhead during traversal, workflow graph DTOs are compiled Ahead-Of-Time into immutable `CompiledWorkflowGraph` instances.

Key Structures:

* **Node Index**: Direct $O(1)$ lookup map `Map<String, WorkflowNodeDto>`.
* **Outgoing Edges Index**: Direct $O(1)$ map `Map<String, List<CompiledEdge>>`.
* **Incoming Edges Index**: Direct $O(1)$ map `Map<String, List<CompiledEdge>>`.
* **Compiled SpEL Bytecode**: Pre-compiled AST expressions for rule node logic and edge traversal conditions.

---

## 27.2 Supported Node Types & Runtime Execution

### 1. `START` Node
Workflow entry point. Initializes runtime context and places initial outgoing edge targets onto active execution frontiers.

### 2. `END` Node
Workflow completion point. Marks `WorkflowInstance` as `COMPLETED`.

### 3. `DECISION` / `RULE` Node
Evaluates pre-compiled SpEL expressions against context. Traverses outgoing edge matching the outcome.

### 4. `COMMAND` Node (SYNC / ASYNC)
Executes system commands (e.g. `UPDATE_FORM_STATUS`, `HTTP_REST`, `MQ_PUBLISH`).

* **Synchronous (SYNC)**: Executed immediately on traversal thread. Results stored under `commandOutputs.<nodeId>`.
* **Asynchronous (ASYNC)**: Registers `EventSubscription` for `COMMAND_RESUME_<nodeId>`. Registers a callback with Spring `TransactionSynchronizationManager` to spawn background execution **after DB transaction commit**. Traversal suspends with `WAITING` status. Upon command completion, `EventRoutingService` correlates event, maps results to context, and resumes traversal.

### 5. `PARALLEL` Node
Fans out execution into multiple parallel branches. Adds all target branch nodes onto `activeFrontiers` and all fanned-out edges into `activeEdges`.

### 6. `JOIN` Node (Converging Sync Point)
Examines all incoming edges against `activeEdges`. If any incoming branch has not arrived, traversal for that branch halts. When all incoming branches arrive, `JOIN` completes and advances traversal.

### 7. `WAIT_EVENT` / `BUCKET` Node
Suspends traversal and registers `EventSubscription` or creates bucket execution task.

### 8. `SUB_WORKFLOW` Node (Call Activity Child Workflow)
Executes an isolated child `WorkflowInstance`. Maps parent context into child input contract. Suspends parent and registers subscription for `CHILD_WORKFLOW_COMPLETED_<childInstanceId>`. Upon child completion, maps output payload into parent context and resumes traversal.

---

# 42. Concurrency, Optimistic Locking & Self-Healing Retries

## 42.1 Optimistic Locking State Protection (`@Version`)

`WorkflowInstance` enforces optimistic concurrency control using JPA `@Version`:

```java
@Version 
@Column(name = "opt_lock_version") 
private Long optLockVersion;
```

This prevents lost updates when parallel execution branches or concurrent inbound event resumptions fire simultaneously across multi-pod clusters.

## 42.2 Self-Healing Concurrent Resumption Retries (`@Retryable`)

When concurrent external events (e.g. parallel REST callbacks or MQ events) arrive simultaneously for the same `businessKey`:

```java
@Retryable(
    retryFor = {ObjectOptimisticLockingFailureException.class, OptimisticLockException.class},
    maxAttempts = 5,
    backoff = @Backoff(delay = 50, multiplier = 2.0, maxDelay = 500, random = true)
)
public TraversalResult resume(String instanceId, String nodeId, Map<String, Object> eventPayload) { ... }
```

If an optimistic lock failure occurs, the conflicting transaction automatically backs off, re-reads the fresh instance state from the database, and re-evaluates the resumption path without dropping events or returning HTTP 500 errors.

## 42.3 Decoupled Foreign Key Architecture

Child logs (`ExecutionLog`) and status records (`RevertStatus`) store parent String IDs directly (`instanceId`, `workflowInstanceId`), eliminating Hibernate entity lock contention and cascading lock delays during high-throughput parallel traversals.

---

# 43. Frontier-Based Parallel Traversal Algorithm

The `GraphTraversalEngine` evaluates concurrent branches using an active frontier tracking mechanism:

```text
                  +-------------------+
                  |   PARALLEL Node   |
                  +---------+---------+
                            |
           +----------------+----------------+
           |                                 |
           v                                 v
+--------------------+            +--------------------+
|  Branch A Frontier |            |  Branch B Frontier |
|  (WAIT_EVENT Node) |            |  (COMMAND Node)    |
+----------+---------+            +----------+---------+
           |                                 |
     State Suspended                 Executes Sync/Async
           |                                 |
           v                                 v
  Wait for Inbound Event             Arrives at JOIN
           |                                 |
     Resumes & Arrives                       v
     at JOIN                     Halts (Waiting for Branch A)
           |                                 |
           +----------------+----------------+
                            |
                            v
                     +--------------+
                     |  JOIN Node   |
                     | (All Arrived)|
                     +------+-------+
                            |
                            v
                     +--------------+
                     |   END Node   |
                     +--------------+
```

# 28. Bucket Architecture

## 28.1 Definition

A Bucket is a business task requiring completion before workflow progression.

Examples:

```text
A2
OBCC
Police Verification
Premium Approval
Fraud Verification
```

---

## 28.2 Why Buckets Are First-Class Entities

Many workflow engines treat tasks as simple nodes.

This platform treats buckets as business objects.

Reason:

Buckets have:

```text
Lifecycle
Ownership
SLA
Status
Escalation
Dependencies
```

---

## 28.3 Bucket Lifecycle

```text
CREATED
   |
READY
   |
IN_PROGRESS
   |
COMPLETED
   |
CLOSED
```

Failure Path:

```text
READY
   |
FAILED
```

---

## 28.4 Bucket Metadata

Example:

```json
{
  "bucketId":"OBCC",
  "ownerGroup":"OBCC_TEAM",
  "slaHours":24
}
```

---

# 29. Dynamic Bucket Generation

## 29.1 Why This Exists

Business wants:

```text
Today:
A2

Tomorrow:
OBCC

Next Month:
Loyal Customer Verification
```

without deployment.

---

## 29.2 Bucket Creation Rule

Example:

```text
IF
AgeOnNetwork > 180

THEN
Create OBCC Bucket
```

Configured via UI.

---

## 29.3 Runtime Behavior

```text
CAF
 |
Rule Evaluation
 |
Bucket Creation
```

Result:

```text
OBCC Bucket Created
```

---

# 30. Bucket Dependency Chains

## 30.1 Business Requirement

Buckets may depend on other buckets.

Example:

```text
A2
 |
 v
Premium Approval
 |
 v
Police Verification
```

---

## 30.2 Dependency Model

```json
{
  "bucket":"POLICE_VERIFICATION",
  "dependsOn":[
      "PREMIUM_APPROVAL"
  ]
}
```

---

## 30.3 Runtime Evaluation

Before execution:

```text
Are dependencies completed?
```

If:

```text
YES
```

Bucket becomes READY.

Otherwise:

```text
WAITING
```

---

# 31. Directed Acyclic Graph (DAG)

## 31.1 Why Traditional State Machines Were Rejected

State Machine:

```text
A -> B -> C
```

Business reality:

```text
      A2
       |
       v

OBCC ---> Activation

       ^
       |

Premium Approval
```

This is a DAG.

---

## 31.2 Chosen Design

Bucket dependencies form a DAG.

Rules create nodes.

Runtime traverses graph.

---

# 32. Rule Evaluation Framework

## 32.1 Purpose

Determine:

```text
Create Bucket?
Skip Bucket?
Transition?
Reject Transaction?
```

---

## 32.2 Rule Components

```text
Condition
Action
```

---

Example:

```text
Condition:
AON > 180

Action:
Create OBCC Bucket
```

---

## 32.3 Rule Types

### Bucket Creation Rule

Creates bucket.

---

### Transition Rule

Moves state.

---

### Validation Rule

Blocks workflow.

---

### Enrichment Rule

Creates derived values.

---

### Routing Rule

Determines execution path.

---

# 33. Expression Engine

## 33.1 Requirements

Support:

```text
AND
OR
NOT
```

---

Comparisons:

```text
=
!=
>
<
>=
<=
```

---

Collections:

```text
IN
NOT IN
```

---

Null Handling:

```text
IS NULL
IS NOT NULL
```

---

Date Functions

---

String Functions

---

Custom Functions

---

# 34. Runtime Transaction Model

## 34.1 Transaction Definition

Every CAF submission creates:

```text
Workflow Instance
```

---

Example:

```json
{
  "instanceId":"WF12345",
  "workflow":"PRE2POST",
  "version":"12"
}
```

---

## 34.2 Why Workflow Instance Exists

Workflow definition is static.

Instance is runtime execution.

---

Example:

```text
Workflow:
PRE2POST v12
```

May have:

```text
1 Million Instances
```

running simultaneously.

---

# 35. Transaction Lifecycle

## 35.1 Submission

```text
CAF Submitted
```

---

## 35.2 Context Acquisition

Required variables loaded.

---

## 35.3 Rule Evaluation

Determine required buckets.

---

## 35.4 Bucket Creation

Create business tasks.

---

## 35.5 Bucket Processing

Business users complete tasks.

---

## 35.6 Dependency Evaluation

Determine newly available buckets.

---

## 35.7 Completion

Workflow completes.

---

# 36. Runtime Status Model

## Workflow Status

```text
CREATED
RUNNING
WAITING
COMPLETED
FAILED
TERMINATED
```

---

## Bucket Status

```text
CREATED
READY
IN_PROGRESS
COMPLETED
FAILED
CANCELLED
```

---

# 37. Event-Driven Runtime

## 37.1 Why Polling Was Rejected

Polling:

```text
Every 5 Seconds
Check Bucket Status
```

Problems:

* Database load
* Latency
* Scalability issues

---

## 37.2 Selected Design

Event Driven.

Example:

```text
Bucket Completed
      |
Publish Event
      |
Workflow Runtime
      |
Evaluate Dependencies
```

---

# 38. Runtime Event Types

## Workflow Events

```text
WORKFLOW_STARTED
WORKFLOW_COMPLETED
WORKFLOW_FAILED
```

---

## Bucket Events

```text
BUCKET_CREATED
BUCKET_READY
BUCKET_COMPLETED
BUCKET_FAILED
```

---

## Context Events

```text
VARIABLE_FETCHED
VARIABLE_CACHE_HIT
```

---

## Rule Events

```text
RULE_EVALUATED
RULE_MATCHED
RULE_SKIPPED
```

---

# 39. Workflow Versioning

## 39.1 Requirement

Running transactions must not break.

---

Bad Example

```text
Transaction started in v10

Workflow changed to v11

Transaction automatically switches
```

Rejected.

---

## Selected Design

Transaction permanently binds to workflow version.

Example:

```text
CAF123
```

uses:

```text
PRE2POST v10
```

until completion.

---

# 40. Draft-Publish Model

## States

```text
DRAFT
REVIEW
APPROVED
PUBLISHED
ARCHIVED
```

---

Benefits:

```text
Governance
Approval Process
Auditability
```

---

# 41. Rollback Strategy

Example:

```text
v10 Published

v11 Published

Production Issue Found
```

Rollback:

```text
v10 Active Again
```

Existing v11 transactions continue on v11.

New transactions use v10.

---

# 42. Parallel Execution

## Example

```text
CAF
 |
 +----> A2
 |
 +----> Fraud Verification
 |
 +----> Premium Approval
```

All can run simultaneously.

---

## Benefits

Reduced turnaround time.

---

# 43. Join Operations

Example:

```text
A2 Completed

Fraud Completed

Premium Completed
```

Only then:

```text
Activation
```

becomes available.

---

# 44. Why This Architecture Was Chosen

Alternative:

```text
Hardcoded Java Flows
```

Rejected because:

* Requires deployments
* Difficult maintenance
* Poor business agility

---

Alternative:

```text
Pure Drools
```

Rejected because:

* Weak orchestration
* No bucket lifecycle
* No DAG execution

---

Alternative:

```text
Pure State Machine
```

Rejected because:

* Cannot dynamically generate execution graph

---

Chosen Design:

```text
Workflow Engine
       +
Bucket Engine
       +
Context Engine
       +
Rule Engine
       +
Execution Journal
```

because it provides:

* Dynamic behavior
* Scalability
* Explainability
* Maintainability
* Business configurability

---

# End of Part 3

Next Part:

Part 4 – Persistence Architecture, Database Schema, Execution Journal, Audit Engine, Replay Framework, Transaction Debugger, Explainability Engine, Observability, Metrics, Logging Strategy and Production Support Architecture.



# Enterprise Workflow Orchestration & Decision Platform

# Engineering Architecture Specification (EAS)

# Part 4 – Persistence Architecture, Execution Journal, Audit Engine, Replay Framework, Transaction Debugger, Explainability Engine, Observability and Production Operations

Version: 1.0

Author: Architecture Specification

Status: Draft

---

# 45. Observability Philosophy

## 45.1 Introduction

For enterprise workflow platforms, execution is not the hardest problem.

The hardest problem is answering:

```text
Why did this happen?
```

Examples:

```text
Why was A2 bucket created?

Why was OBCC skipped?

Why did activation fail?

Why did workflow terminate?

Why did fraud verification trigger?

Which API response caused the decision?

Which workflow version processed this CAF?

Which rule evaluated to TRUE?
```

Traditional enterprise systems often fail because they cannot answer these questions quickly.

---

## 45.2 Design Goal

The platform must provide complete execution visibility.

Every business decision must be explainable.

Every execution path must be traceable.

Every workflow must be reproducible.

Every transaction must be debuggable.

---

## 45.3 Core Principle

The debugger is not an afterthought.

The debugger is a first-class subsystem.

Architecture:

```text
Workflow Runtime
        |
        +------ Execution Journal
        |
        +------ Audit Engine
        |
        +------ Replay Engine
        |
        +------ Explainability Engine
        |
        +------ Visual Debugger
```

---

# 46. Deferred In-Memory Task Persistence & Database Schema

## 46.1 `TaskRecorder` Deferred In-Memory Batch Persistence

To achieve high concurrent throughput and eliminate HikariCP database pool connection locks during traversal, task instance state transitions are not written to the database step-by-step.

```text
         +-------------------------------------------------------------+
         |                     GraphTraversalEngine                    |
         |  - Step 1: Execute Node A -> TaskRecorder.recordTask(...)   |
         |  - Step 2: Evaluate Rule  -> $0 ms RAM Context Lookup      |
         |  - Step 3: Execute Node B -> TaskRecorder.recordTask(...)   |
         +------------------------------+------------------------------+
                                        |
                                        | Traversal Boundary
                                        | (COMPLETED / WAITING / FAILED)
                                        v
         +-------------------------------------------------------------+
         |                 TaskRecorder.flushPendingTasks()             |
         |                 - Single JDBC Batch INSERT / UPDATE         |
         |                 - HikariCP Connection Lock Duration: < 1 ms |
         +-------------------------------------------------------------+
```

Key Architecture Characteristics:

* **In-Memory Accumulation**: Collects `TaskInstance` state transitions (`RUNNING` $\rightarrow$ `COMPLETED` / `FAILED`) inside `TraversalContext.inMemoryTasks`.
* **Read-Your-Own-Writes**: Downstream nodes (`JOIN`, SpEL rule evaluation) resolve prior step outputs from `TraversalContext.inMemoryTasks` with $0\text{ ms}$ RAM lookups.
* **Single Batch DB Flush**: At traversal boundaries (`COMPLETED`, suspension `WAITING`, or `FAILED`), `TaskRecorder.flushPendingTasks(instanceId)` saves all accumulated tasks in a single `saveAll()` batch, eliminating $2N$ per-step SQL roundtrips and database connection locks.

---

# 47. Database Schema Specifications

## 47.1 `WORKFLOW_INSTANCE` Table

```sql
CREATE TABLE workflow_instance (
    id VARCHAR(255) PRIMARY KEY,
    workflow_definition_id VARCHAR(255) NOT NULL,
    workflow_version_id VARCHAR(255) NOT NULL,
    business_key VARCHAR(255) NOT NULL, -- e.g. CAF_ID or MSISDN
    status VARCHAR(50) NOT NULL,       -- RUNNING, WAITING, COMPLETED, FAILED
    serialized_context CLOB,           -- JSON execution context
    runtime_graph CLOB,                -- Active frontier state JSON
    opt_lock_version BIGINT DEFAULT 0, -- JPA @Version Optimistic Locking Column
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_wf_inst_biz_key ON workflow_instance(business_key);
```

---

## 47.2 `TASK_INSTANCE` Table

```sql
CREATE TABLE task_instance (
    id VARCHAR(255) PRIMARY KEY,
    workflow_instance_id VARCHAR(255) NOT NULL,
    node_id VARCHAR(255) NOT NULL,
    task_type VARCHAR(50) NOT NULL,    -- SYNC_RULE, COMMAND, ASYNC_WAIT, BUCKET
    label VARCHAR(255),
    status VARCHAR(50) NOT NULL,       -- RUNNING, COMPLETED, FAILED, SUSPENDED
    input_payload CLOB,
    output_payload CLOB,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);
CREATE INDEX idx_task_inst_wf_id ON task_instance(workflow_instance_id);
```

---

## 47.3 `EVENT_SUBSCRIPTION` Table

```sql
CREATE TABLE event_subscription (
    id VARCHAR(255) PRIMARY KEY,
    business_key VARCHAR(255) NOT NULL,  -- Correlated Business Key (e.g. CAF_ID)
    event_type VARCHAR(255) NOT NULL,    -- e.g. PAYMENT_RECEIVED, COMMAND_RESUME_cmd1
    filter_attributes CLOB,              -- JSON key-value filters
    target_node_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,         -- ACTIVE, FULFILLED, CANCELLED
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_event_sub_lookup ON event_subscription(business_key, event_type, status);
```

---

## 47.4 Decoupled High-Churn Logs (`EXECUTION_LOG` & `REVERT_STATUS`)

To eliminate foreign key database locks and cascading Hibernate locks during high-throughput traversals, execution logs store parent String IDs directly without `@ManyToOne` foreign key constraints:

```sql
CREATE TABLE execution_log (
    id VARCHAR(255) PRIMARY KEY,
    instance_id VARCHAR(255) NOT NULL, -- Decoupled String ID reference
    node_id VARCHAR(255) NOT NULL,
    step_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    details CLOB,
    timestamp TIMESTAMP NOT NULL
);

CREATE TABLE revert_status (
    id VARCHAR(255) PRIMARY KEY,
    workflow_instance_id VARCHAR(255) NOT NULL, -- Decoupled String ID reference
    node_id VARCHAR(255) NOT NULL,
    revert_state VARCHAR(50) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```
  "ageOnNetwork":220,
  "arpu":350
}
```

---

## 49.2 Context Table

```sql
WORKFLOW_CONTEXT
```

Columns:

```text
INSTANCE_ID
CONTEXT_JSON
LAST_UPDATED
```

---

## 49.3 Snapshot Strategy

Context snapshots should be taken at major milestones.

Examples:

```text
Before Rule Evaluation

After Rule Evaluation

Before Bucket Creation

After Bucket Completion
```

---

Benefits:

```text
Replay
Debugging
Audit
```

---

# 50. Bucket Persistence

## 50.1 Bucket Instance Table

```sql
BUCKET_INSTANCE
```

Columns:

```text
BUCKET_ID
INSTANCE_ID
BUCKET_NAME
STATUS
OWNER
CREATED_TIME
COMPLETED_TIME
```

---

## 50.2 Dependency Table

```sql
BUCKET_DEPENDENCY
```

Columns:

```text
PARENT_BUCKET
CHILD_BUCKET
```

---

# 51. Execution Journal

## 51.1 Purpose

Execution Journal is the foundation of debugging.

Every meaningful activity becomes an event.

---

## 51.2 Why Traditional Logs Were Rejected

Traditional logs:

```text
INFO Started Workflow

INFO Rule Executed

INFO API Called
```

Problems:

```text
Unstructured
Difficult To Query
Difficult To Replay
Difficult To Visualize
```

---

## 51.3 Chosen Design

Structured Event Journal.

---

Example:

```json
{
  "eventType":"RULE_EVALUATED",
  "rule":"OBCC_RULE",
  "result":true,
  "timestamp":"..."
}
```

---

# 52. Execution Event Model

## Base Structure

```json
{
  "eventId":"...",
  "instanceId":"...",
  "timestamp":"...",
  "eventType":"..."
}
```

---

# 53. Event Types

## Workflow Events

```text
WORKFLOW_STARTED

WORKFLOW_COMPLETED

WORKFLOW_FAILED

WORKFLOW_TERMINATED
```

---

## Context Events

```text
VARIABLE_REQUESTED

VARIABLE_RESOLVED

VARIABLE_CACHE_HIT

VARIABLE_RESOLUTION_FAILED
```

---

## Rule Events

```text
RULE_ENTERED

RULE_EVALUATED

RULE_MATCHED

RULE_SKIPPED
```

---

## Bucket Events

```text
BUCKET_CREATED

BUCKET_READY

BUCKET_STARTED

BUCKET_COMPLETED

BUCKET_FAILED
```

---

## Integration Events

```text
API_CALLED

API_RESPONSE

MQ_REQUEST

MQ_RESPONSE

DB_QUERY_EXECUTED
```

---

## Error Events

```text
EXCEPTION

TIMEOUT

RETRY

CIRCUIT_OPEN
```

---

# 54. Transaction Timeline View

## Objective

Provide a chronological view.

Example:

```text
10:01:00
Workflow Started

10:01:01
Customer Type Resolved

10:01:03
Rule Evaluated

10:01:05
OBCC Bucket Created

10:01:08
Activation Failed
```

---

## UI Representation

```text
────────────────────

Workflow Started

↓

Customer Type Fetched

↓

Rule Evaluated

↓

OBCC Bucket Created

↓

Activation Failed

────────────────────
```

---

# 55. Visual Workflow Debugger

## Objective

Display execution directly on workflow graph.

Example:

```text
START           ✓

Customer Check  ✓

OBCC Rule       ✓

OBCC Bucket     ✓

Activation      ✗
```

---

## Node Coloring

### Green

```text
Executed Successfully
```

---

### Yellow

```text
Waiting
```

---

### Red

```text
Failed
```

---

### Grey

```text
Not Executed
```

---

# 56. Node Inspection Panel

Clicking a node displays execution details.

Example:

```json
{
  "node":"OBCC_RULE",
  "result":true,
  "duration":15
}
```

---

# 57. Rule Evaluation Trace

## Problem

Knowing:

```text
Rule Result = FALSE
```

is not enough.

---

Need:

```text
WHY FALSE?
```

---

Example Rule

```text
CustomerType = PREPAID

AND

AgeOnNetwork > 180
```

---

Trace:

```text
CustomerType = PREPAID

Result = TRUE

AgeOnNetwork = 120

Result = FALSE

Final Result = FALSE
```

---

# 58. Explainability Engine

## Purpose

Answer business questions.

Examples:

```text
Why was OBCC created?

Why was A2 skipped?

Why was activation rejected?
```

---

## Explainability Model

Every decision stores:

```text
Condition

Variables

Evaluation Result
```

---

Example:

```json
{
  "decision":"OBCC",
  "condition":"AON > 180",
  "value":220,
  "result":true
}
```

---

# 59. "Why?" Queries

## Example

Question:

```text
Why was OBCC created?
```

---

Response:

```text
Rule:
OBCC_RULE

Condition:
AGE_ON_NETWORK > 180

Actual Value:
220

Result:
TRUE
```

---

# 60. "Why Not?" Queries

## Example

Question:

```text
Why was Police Verification skipped?
```

---

Response:

```text
Rule:
FOREIGNER_CHECK

Condition:
CUSTOMER_TYPE = FOREIGNER

Actual Value:
INDIAN

Result:
FALSE
```

---

# 61. Context Resolution Trace

## Purpose

Show source of variables.

Example:

```text
AGE_ON_NETWORK
```

resolved through:

```text
CRM_AON_PROVIDER
```

---

Trace:

```json
{
  "variable":"AGE_ON_NETWORK",
  "provider":"CRM_AON_PROVIDER",
  "value":220,
  "durationMs":325,
  "cacheHit":false
}
```

---

# 62. Integration Debugging

## Objective

Expose integration behavior.

Example:

```text
CRM_AON_PROVIDER
```

---

Captured Data:

```text
Start Time

End Time

Latency

Status

Retry Count
```

---

## Optional Payload Capture

Configurable.

```text
Capture Request Payload

Capture Response Payload
```

---

Security controls required.

---

# 63. Replay Framework

## Objective

Reproduce historical executions.

---

Example:

```text
CAF12345
```

processed yesterday.

Business reports issue.

---

Investigator clicks:

```text
Replay
```

---

Replay loads:

```text
Workflow Version

Context Snapshot

Execution Inputs
```

---

Then re-runs workflow.

---

# 64. Version-Aware Replay

Critical Requirement.

Replay must use:

```text
Original Workflow Version
```

Not:

```text
Current Workflow Version
```

---

Otherwise replay becomes invalid.

---

# 65. Audit Engine

## Objective

Provide immutable history.

---

Audit records:

```text
Who Published Workflow?

Who Modified Workflow?

Who Approved Workflow?

Who Triggered Replay?

Who Completed Bucket?
```

---

# 66. Audit Event Storage

```sql
AUDIT_EVENT
```

Columns:

```text
EVENT_ID

USER_ID

ACTION

ENTITY_TYPE

ENTITY_ID

TIMESTAMP
```

---

# 67. Metrics Framework

## Objective

Measure platform health.

---

Metrics:

```text
Workflow Throughput

Bucket Throughput

Average Latency

Error Rate

API Failures

Rule Evaluation Time
```

---

# 68. Business Metrics

Examples:

```text
OBCC Created Today

A2 Pending

Police Verification Pending

Premium Approval Pending
```

---

# 69. Operational Metrics

Examples:

```text
Context Resolution Time

Cache Hit Rate

Provider Failures

Queue Backlog

Workflow Backlog
```

---

# 70. Logging Strategy

## Levels

### INFO

Business milestones.

---

### DEBUG

Execution details.

---

### TRACE

Deep diagnostics.

---

### ERROR

Failures.

---

# 71. Distributed Tracing

Every workflow execution receives:

```text
CORRELATION_ID
```

Example:

```text
CAF12345
```

---

All:

```text
Logs

Events

API Calls

MQ Calls
```

must contain this ID.

---

# 72. Failure Analysis Dashboard

Should display:

```text
Top Failed Workflows

Top Failed Providers

Most Common Rule Failures

Most Common Bucket Failures
```

---

# 73. Operational Recovery

## Manual Retry

Retry failed node.

---

## Bucket Retry

Retry bucket execution.

---

## Workflow Resume

Resume paused workflow.

---

## Workflow Restart

Start from beginning.

---

# 74. Production Support Features

## Search by

```text
CAF ID

MSISDN

Workflow Instance ID

Bucket ID

Correlation ID
```

---

## Filters

```text
Date Range

Workflow Version

Status

Failure Type
```

---

# 75. Why This Design Was Chosen

Alternative:

```text
Only Logs
```

Rejected because:

```text
Poor Visibility

Poor Explainability

Slow Investigations
```

---

Alternative:

```text
Database State Only
```

Rejected because:

```text
No Decision History

No Replay

No Root Cause Analysis
```

---

Selected Design:

```text
Execution Journal
+
Replay Engine
+
Explainability Engine
+
Visual Debugger
+
Audit Engine
```

because it provides:

```text
Trust

Traceability

Supportability

Operational Excellence

Regulatory Compliance
```

---

# End of Part 4

Next Part:

Part 5 – Frontend Architecture (React + XYFlow), UX Design, Workflow Designer, Context Catalog UI, Bucket Designer, Rule Designer, Version Management, Governance Model, Security Architecture, Multi-Tenancy, RBAC, Deployment Architecture, Kubernetes, Scaling, High Availability, and Enterprise Production Readiness.

This final part will convert the platform from an engine specification into a complete enterprise product specification.


# Enterprise Workflow Orchestration & Decision Platform

# Engineering Architecture Specification (EAS)

# Part 5 – Frontend Architecture, UX, Governance, Security, Multi-Tenancy, Deployment, Scalability and Enterprise Production Readiness

Version: 1.0

Author: Architecture Specification

Status: Draft

---

# 76. Frontend Architecture Overview

## 76.1 Objectives

The frontend is not merely a configuration screen.

It is the primary interface through which:

* Business users define workflows
* Architects define execution models
* Operations teams investigate failures
* Administrators manage versions
* Support teams debug transactions

The frontend must support:

```text
Workflow Design
Rule Configuration
Bucket Configuration
Context Catalog Management
Execution Visualization
Replay
Debugging
Audit Investigation
Version Management
```

---

## 76.2 Technology Stack

### Core

```text
React
TypeScript
```

---

### Workflow Visualization

```text
XYFlow
```

Reason:

```text
Node Based Architecture
Custom Node Types
Scalability
Zoom Support
Mini Map
Large Graph Handling
```

---

### UI Framework

Since the target implementation stack already uses MUI:

```text
Material UI (MUI)
```

---

### State Management

```text
Zustand
```

Reason:

```text
Simple
Performant
Less Boilerplate
```

---

### Server Communication

```text
React Query
```

Purpose:

```text
Caching
Retries
Background Refresh
```

---

# 77. User Personas

## Business User

Can:

```text
Create Rules
Modify Conditions
Configure Buckets
Publish Workflows
```

Cannot:

```text
Create Integrations
Modify Providers
Modify Runtime Engine
```

---

## Solution Architect

Can:

```text
Design Workflow
Configure Dependencies
Review Execution Paths
```

---

## Integration Developer

Can:

```text
Create Context Providers
Create Integrations
Configure API Connectors
```

---

## Operations User

Can:

```text
View Transactions
Debug Failures
Replay Transactions
```

---

## Administrator

Can:

```text
Manage Tenants
Manage Users
Assign Permissions
```

---

# 78. Workflow Designer

## 78.1 Overview

Workflow Designer is the core visual experience.

Built using XYFlow.

---

## 78.2 Supported Nodes

### Start Node

```text
Workflow Entry Point
```

---

### End Node

```text
Workflow Exit Point
```

---

### Decision Node

```text
IF
ELSE
SWITCH
```

---

### Bucket Node

```text
A2
OBCC
Premium Approval
```

---

### Rule Node

```text
Business Rule
```

---

### Parallel Node

```text
Fork Execution
```

---

### Join Node

```text
Merge Execution
```

---

### Timer Node

```text
Wait
Delay
SLA Check
```

---

### Manual Task Node

```text
Human Action Required
```

---

### System Action Node

```text
Automatic Processing
```

---

# 79. Workflow Validation Engine

## Purpose

Prevent invalid workflow definitions.

---

Validation Rules:

### Missing Start Node

Reject.

---

### Missing End Node

Reject.

---

### Circular Dependency

Reject.

---

### Unreachable Node

Warn.

---

### Dependency Loop

Reject.

---

### Invalid Variable Reference

Reject.

---

# 80. Context Catalog UI

## Purpose

Expose business variables.

---

Business user sees:

```text
Age On Network

Customer Segment

ARPU

Dealer Category

Fraud Score
```

---

Business user never sees:

```text
SOAP Endpoint

REST URL

MQ Queue
```

---

## Variable Details

Each variable displays:

```text
Name

Description

Owner

Provider

Data Type

Cache Policy

Cost
```

---

# 81. Context Dependency Viewer

## Purpose

Show variable relationships.

Example:

```text
LOYAL_CUSTOMER
       |
       +---- AGE_ON_NETWORK
       |
       +---- ARPU
```

---

Benefits:

```text
Explainability

Impact Analysis

Debugging
```

---

# 82. Rule Designer

## Objective

Allow business users to create rules without coding.

---

Example:

```text
IF

Age On Network > 180

AND

Customer Type = PREPAID

THEN

Create OBCC Bucket
```

---

## Supported Operators

```text
=
!=
>
<
>=
<=
```

---

Collections:

```text
IN
NOT IN
```

---

Null:

```text
IS NULL
IS NOT NULL
```

---

Logical:

```text
AND
OR
NOT
```

---

# 83. Bucket Designer

## Purpose

Manage bucket definitions.

---

Properties:

```text
Name

Description

Owner Group

SLA

Priority

Dependencies
```

---

Example:

```text
Bucket:
OBCC

Owner:
OBCC_TEAM

SLA:
24 Hours
```

---

# 84. Version Management UI

## Lifecycle

```text
DRAFT

REVIEW

APPROVED

PUBLISHED

ARCHIVED
```

---

## Compare Versions

Must support:

```text
Version 10 vs Version 11
```

Display:

```text
Rules Added

Rules Removed

Nodes Changed

Dependencies Changed
```

---

# 85. Governance Framework

## Why Governance Exists

Without governance:

```text
Anyone Can Publish
```

which becomes dangerous.

---

## Proposed Workflow

```text
Author

↓

Reviewer

↓

Approver

↓

Publisher
```

---

# 86. Security Architecture

## Authentication

Recommended:

```text
Keycloak
```

---

Supports:

```text
OIDC

OAuth2

SAML
```

---

## Authorization

Role Based Access Control.

---

# 87. RBAC Model

## Roles

### Workflow Author

Can:

```text
Create Drafts
```

---

### Workflow Reviewer

Can:

```text
Review
Comment
```

---

### Workflow Approver

Can:

```text
Approve
Reject
```

---

### Publisher

Can:

```text
Publish
Rollback
```

---

### Operations

Can:

```text
View Transactions
Replay
Debug
```

---

### Admin

Full access.

---

# 88. Multi-Tenancy

## Why Multi-Tenancy

Future possibility:

```text
Multiple Business Units

Multiple Circles

Multiple Customers
```

---

## Tenant Isolation

Each tenant has:

```text
Workflow Definitions

Context Variables

Buckets

Users

Audit Logs
```

isolated.

---

# 89. Data Isolation Strategy

Recommended:

```text
Shared Database

Tenant Column
```

---

Enterprise Option:

```text
Separate Schema Per Tenant
```

---

# 90. API Architecture

## Backend APIs

### Workflow APIs

```text
Create Workflow

Update Workflow

Publish Workflow
```

---

### Rule APIs

```text
Create Rule

Update Rule

Validate Rule
```

---

### Context APIs

```text
List Variables

Resolve Variable

View Dependencies
```

---

### Debug APIs

```text
Execution Timeline

Replay

Explain Decision
```

---

# 91. Deployment Architecture

## Containerization

All services containerized.

```text
Docker
```

---

# 92. Kubernetes Architecture

## Services

```text
Workflow Service

Execution Service

Context Service

Integration Service

Audit Service

UI Service
```

---

# 93. High Availability

## Requirements

No Single Point Of Failure.

---

All services:

```text
Minimum 2 Pods
```

---

Database:

```text
Primary

Replica
```

---

Redis:

```text
Cluster Mode
```

---

# 94. Scalability Strategy

## Horizontal Scaling

Execution nodes stateless.

---

Can scale:

```text
2 Pods

10 Pods

100 Pods
```

without code changes.

---

# 95. Event Infrastructure

Recommended:

```text
Kafka
```

---

Events:

```text
Bucket Created

Bucket Completed

Workflow Started

Workflow Failed
```

---

Benefits:

```text
Decoupling

Scalability

Replay
```

---

# 96. Distributed Cache

Recommended:

```text
Redis
```

Used for:

```text
Variable Cache

Execution Cache

Session Data
```

---

# 97. Failure Recovery

## Pod Crash

Workflow resumes from persisted state.

---

## Node Crash

Event replay restores execution.

---

## Database Failover

Automatic recovery.

---

# 98. SLA Framework

Each bucket may define:

```text
Warning Threshold

Breach Threshold

Escalation Rule
```

---

Example:

```text
Warning:
18 Hours

Breach:
24 Hours
```

---

# 99. Notifications

Supported Channels:

```text
Email

SMS

Webhook

Kafka Event
```

---

Events:

```text
Bucket Breach

Workflow Failure

Approval Required
```

---

# 100. Production Readiness Checklist

## Functional

```text
Versioning

Replay

Explainability

Audit

Rollback
```

---

## Operational

```text
Metrics

Monitoring

Tracing

Alerting
```

---

## Security

```text
Authentication

Authorization

Audit Trail
```

---

## Scalability

```text
Horizontal Scaling

Distributed Cache

Event Driven Runtime
```

---

# 101. Future Enhancements

## AI Assisted Rule Creation

Example:

```text
Create OBCC bucket if
AON > 180
and customer is prepaid
```

AI generates rule definition.

---

## AI Explainability Assistant

Question:

```text
Why was OBCC created?
```

AI explains execution path.

---

## AI Workflow Optimization

Detect:

```text
Unused Variables

Duplicate Rules

Expensive Conditions
```

---

# 102. Final Architecture Summary

The production platform architecture consists of an integrated multi-tier stack:

```text
               +---------------------------------------------------+
               | React + XYFlow Canvas UI (atlas-ui)              |
               +-------------------------+-------------------------+
                                         |
                                         v
               +---------------------------------------------------+
               | Multi-Level Graph Cache & AOT Compiler            |
               | - L1: Caffeine JVM In-Memory Cache                |
               | - L2: Redis Pub/Sub Cluster Invalidation          |
               | - AOT SpEL Bytecode Evaluator (IMMEDIATE)          |
               +-------------------------+-------------------------+
                                         |
                                         v
               +---------------------------------------------------+
               | GraphTraversalEngine & Loom Concurrency Manager   |
               | - Frontier-based Parallel Traversal               |
               | - Java 21 Virtual Thread Per Task                 |
               | - Non-blocking External I/O Offloading (isExternal)|
               | - Optimistic Locking Self-Healing (@Retryable)    |
               +-------------------------+-------------------------+
                                         |
                                         v
               +---------------------------------------------------+
               | Deferred Task Persistence & Context Engine        |
               | - TaskRecorder In-Memory Batching                 |
               | - LazyContextMap & Namespaced Command Outputs     |
               | - Single Batch JDBC Database Commit               |
               +---------------------------------------------------+
```

The platform is intentionally governed by these core architectural tenets:

```text
Context First & Lazy Resolution

Ahead-Of-Time SpEL Bytecode Compilation

Zero-Allocation Topological Graph Indexing

Deferred In-Memory Batch Task Persistence

Java 21 Non-Blocking Virtual Thread Execution

Self-Healing Optimistic Locking Retries

Contract-Based Task & Sub-Workflow Orchestration

Cluster-Wide Multi-Level Cache Synchronization
```

By decoupling business rule configuration, process orchestration, and external I/O integrations into dynamic metadata, future business journeys (such as new verification buckets, approval flows, or fraud validation paths) are deployed in seconds through the visual canvas UI without requiring application code changes or software releases.

# End of Engineering Architecture Specification (Parts 1–5)
