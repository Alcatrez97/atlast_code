-- =============================================================================
-- ATLAS WORKFLOW & DECISION ENGINE - ORACLE DATABASE DDL SCRIPT
-- =============================================================================
-- Target Database: Oracle Database 12c / 18c / 19c / 21c / 23c
-- Generated Date: 2026-09-24
-- Description: Complete production-ready DDL script with 'postpaid_' prefix for all 18 tables,
--              including DROP statements, TABLE definitions, PRIMARY KEYS, FOREIGN KEYS,
--              UNIQUE CONSTRAINTS, CHECK CONSTRAINTS, and INDEXES.
--              Fully synchronized with JPA Entities in com.vi.atlas.workflow.entity.*
-- =============================================================================

SET DEFINE OFF;
ALTER SESSION SET NLS_TIMESTAMP_FORMAT = 'YYYY-MM-DD HH24:MI:SS.FF';

-- =============================================================================
-- SECTION 1: CLEANUP / DROP TABLES (SAFE CASCADE DROPS)
-- =============================================================================

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE postpaid_workflow_execution_log_details CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE postpaid_workflow_bucket_executions CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE postpaid_workflow_execution_logs CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE postpaid_workflow_revert_status CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE postpaid_workflow_event_subscriptions CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE postpaid_workflow_task_instances CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE postpaid_workflow_instances CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE postpaid_workflow_versions CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE postpaid_workflow_definitions CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE postpaid_workflow_context_fields CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE postpaid_workflow_context_schemas CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE postpaid_workflow_integration_registry CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE postpaid_workflow_event_definitions CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE postpaid_workflow_rules CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE postpaid_workflow_buckets CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE POSTPAID_ONBOARD_CAF CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE POSTPAID_ONBOARD_COCP CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE postpaid_workflow_staged_payloads CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

-- Legacy table drops (safe backward-compatibility cleanup)
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE workflow_execution_log_details CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE workflow_bucket_executions CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE workflow_execution_logs CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE workflow_revert_status CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE workflow_event_subscriptions CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE workflow_task_instances CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE workflow_instances CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE workflow_versions CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE workflow_definitions CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE workflow_context_fields CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE workflow_context_schemas CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE workflow_integration_registry CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE workflow_event_definitions CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE workflow_rules CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE workflow_buckets CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE workflow_customer_forms CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE workflow_staged_payloads CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/


-- =============================================================================
-- SECTION 2: WORKFLOW CORE DEFINITION & VERSIONING TABLES
-- =============================================================================

-- 1. POSTPAID WORKFLOW DEFINITIONS TABLE
CREATE TABLE postpaid_workflow_definitions (
    workflow_definition_pk  VARCHAR2(36 CHAR) NOT NULL,
    name                    VARCHAR2(255 CHAR) NOT NULL,
    wf_key                  VARCHAR2(100 CHAR) NOT NULL,
    description             VARCHAR2(1000 CHAR),
    active_version          NUMBER(10,0),
    active                  NUMBER(1,0) DEFAULT 1 NOT NULL,
    circle_id               NUMBER(10,0),
    created_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_workflow_def PRIMARY KEY (workflow_definition_pk),
    CONSTRAINT uq_wf_def_key UNIQUE (wf_key),
    CONSTRAINT chk_wf_def_active CHECK (active IN (0, 1))
);

CREATE UNIQUE INDEX idx_wf_def_key ON postpaid_workflow_definitions(wf_key);


-- 2. POSTPAID WORKFLOW VERSIONS TABLE
CREATE TABLE postpaid_workflow_versions (
    workflow_version_pk     VARCHAR2(36 CHAR) NOT NULL,
    workflow_definition_id  VARCHAR2(36 CHAR) NOT NULL,
    version                 NUMBER(10,0) NOT NULL,
    status                  VARCHAR2(20 CHAR) NOT NULL, -- DRAFT, REVIEW, APPROVED, PUBLISHED
    definition_json         CLOB NOT NULL,
    created_by              VARCHAR2(100 CHAR),
    updated_by              VARCHAR2(100 CHAR),
    circle_id               NUMBER(10,0),
    created_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_workflow_ver PRIMARY KEY (workflow_version_pk),
    CONSTRAINT uq_wf_version UNIQUE (workflow_definition_id, version),
    CONSTRAINT fk_ver_wf_def FOREIGN KEY (workflow_definition_id) 
        REFERENCES postpaid_workflow_definitions(workflow_definition_pk) ON DELETE CASCADE
);

CREATE INDEX idx_wf_ver_status ON postpaid_workflow_versions(status);
CREATE INDEX idx_wf_ver_def_id ON postpaid_workflow_versions(workflow_definition_id);


-- =============================================================================
-- SECTION 3: WORKFLOW RUNTIME EXECUTION & TASK TABLES
-- =============================================================================

-- 3. POSTPAID WORKFLOW INSTANCES TABLE
CREATE TABLE postpaid_workflow_instances (
    workflow_instance_pk    VARCHAR2(36 CHAR) NOT NULL,
    workflow_key            VARCHAR2(100 CHAR) NOT NULL,
    version_id              VARCHAR2(36 CHAR) NOT NULL,
    version_number          NUMBER(10,0) NOT NULL,
    status                  VARCHAR2(30 CHAR) NOT NULL, -- CREATED, RUNNING, WAITING, COMPLETED, FAILED, TERMINATED
    current_node_id         VARCHAR2(100 CHAR),
    business_key            VARCHAR2(100 CHAR),
    circle_id               NUMBER(10,0),
    opt_lock_version        NUMBER(19,0) DEFAULT 0,
    serialized_context      CLOB,
    runtime_graph           CLOB,
    created_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_workflow_inst PRIMARY KEY (workflow_instance_pk),
    CONSTRAINT fk_inst_version FOREIGN KEY (version_id) 
        REFERENCES postpaid_workflow_versions(workflow_version_pk)
);

CREATE INDEX idx_inst_wf_key ON postpaid_workflow_instances(workflow_key);
CREATE INDEX idx_inst_status ON postpaid_workflow_instances(status);
CREATE INDEX idx_inst_created_at ON postpaid_workflow_instances(created_at);
CREATE INDEX idx_inst_biz_key ON postpaid_workflow_instances(business_key);
CREATE INDEX idx_inst_version_id ON postpaid_workflow_instances(version_id);


-- 4. POSTPAID WORKFLOW TASK INSTANCES TABLE
CREATE TABLE postpaid_workflow_task_instances (
    task_instance_pk        VARCHAR2(255 CHAR) NOT NULL,
    instance_id             VARCHAR2(36 CHAR) NOT NULL,
    task_type               VARCHAR2(50 CHAR) NOT NULL,
    label                   VARCHAR2(200 CHAR) NOT NULL,
    status                  VARCHAR2(30 CHAR) NOT NULL,
    circle_id               NUMBER(10,0),
    input_data              CLOB,
    output_data             CLOB,
    started_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    completed_at            TIMESTAMP(6),
    CONSTRAINT pk_task_inst PRIMARY KEY (task_instance_pk),
    CONSTRAINT fk_task_inst_workflow FOREIGN KEY (instance_id) 
        REFERENCES postpaid_workflow_instances(workflow_instance_pk) ON DELETE CASCADE
);

CREATE INDEX idx_task_inst_parent ON postpaid_workflow_task_instances(instance_id);
CREATE INDEX idx_task_inst_status ON postpaid_workflow_task_instances(status);
CREATE INDEX idx_task_inst_type ON postpaid_workflow_task_instances(task_type);


-- 5. POSTPAID WORKFLOW EVENT SUBSCRIPTIONS TABLE
CREATE TABLE postpaid_workflow_event_subscriptions (
    event_subscription_pk   VARCHAR2(36 CHAR) NOT NULL,
    instance_id             VARCHAR2(36 CHAR) NOT NULL,
    business_key            VARCHAR2(100 CHAR) NOT NULL,
    event_type              VARCHAR2(100 CHAR) NOT NULL,
    target_node_id          VARCHAR2(100 CHAR) NOT NULL,
    status                  VARCHAR2(30 CHAR) DEFAULT 'ACTIVE' NOT NULL, -- ACTIVE, TRIGGERED, CANCELLED
    filter_attributes       CLOB,
    circle_id               NUMBER(10,0),
    created_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_event_sub PRIMARY KEY (event_subscription_pk),
    CONSTRAINT fk_sub_workflow_instance FOREIGN KEY (instance_id) 
        REFERENCES postpaid_workflow_instances(workflow_instance_pk) ON DELETE CASCADE
);

CREATE INDEX idx_event_sub_bkey ON postpaid_workflow_event_subscriptions(business_key);
CREATE INDEX idx_event_sub_type ON postpaid_workflow_event_subscriptions(event_type);
CREATE INDEX idx_event_sub_status ON postpaid_workflow_event_subscriptions(status);
CREATE INDEX idx_event_sub_instance ON postpaid_workflow_event_subscriptions(instance_id);
CREATE INDEX idx_event_sub_lookup ON postpaid_workflow_event_subscriptions(business_key, event_type, status);


-- =============================================================================
-- SECTION 4: AUDIT, LOGGING & REVERT TABLES (VERTICAL PARTITIONING)
-- =============================================================================

-- 6. POSTPAID WORKFLOW EXECUTION LOGS TABLE (LIGHTWEIGHT SUMMARY)
CREATE TABLE postpaid_workflow_execution_logs (
    execution_log_pk        VARCHAR2(36 CHAR) NOT NULL,
    workflow_key            VARCHAR2(100 CHAR) NOT NULL,
    version_id              VARCHAR2(36 CHAR),
    version_number          NUMBER(10,0),
    context_id              VARCHAR2(100 CHAR),
    instance_id             VARCHAR2(36 CHAR),
    circle_id               NUMBER(10,0),
    status                  VARCHAR2(20 CHAR) NOT NULL,
    outcome_node_id         VARCHAR2(100 CHAR),
    outcome_node_label      VARCHAR2(255 CHAR),
    outcome_bucket_id       VARCHAR2(100 CHAR),
    step_count              NUMBER(10,0),
    error_message           VARCHAR2(1000 CHAR),
    total_duration_ms       NUMBER(19,0),
    trace_json              CLOB,
    started_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    completed_at            TIMESTAMP(6),
    CONSTRAINT pk_exec_log PRIMARY KEY (execution_log_pk),
    CONSTRAINT fk_exec_log_version FOREIGN KEY (version_id) 
        REFERENCES postpaid_workflow_versions(workflow_version_pk) ON DELETE SET NULL
);

CREATE INDEX idx_exec_wf_key ON postpaid_workflow_execution_logs(workflow_key);
CREATE INDEX idx_exec_status ON postpaid_workflow_execution_logs(status);
CREATE INDEX idx_exec_started_at ON postpaid_workflow_execution_logs(started_at);
CREATE INDEX idx_exec_inst_id ON postpaid_workflow_execution_logs(instance_id);
CREATE INDEX idx_exec_version_id ON postpaid_workflow_execution_logs(version_id);


-- 7. POSTPAID WORKFLOW EXECUTION LOG DETAILS TABLE (VERTICAL PARTITION 1:1 WITH EXECUTION LOG)
CREATE TABLE postpaid_workflow_execution_log_details (
    log_id                  VARCHAR2(36 CHAR) NOT NULL,
    input_context_json      CLOB,
    execution_trace_json    CLOB,
    CONSTRAINT pk_exec_detail PRIMARY KEY (log_id),
    CONSTRAINT fk_exec_detail_log FOREIGN KEY (log_id) 
        REFERENCES postpaid_workflow_execution_logs(execution_log_pk) ON DELETE CASCADE
);


-- 8. POSTPAID WORKFLOW REVERT STATUS TABLE
CREATE TABLE postpaid_workflow_revert_status (
    revert_status_pk        VARCHAR2(36 CHAR) NOT NULL,
    workflow_instance_id    VARCHAR2(36 CHAR) NOT NULL,
    form_id                 VARCHAR2(100 CHAR) NOT NULL,
    bucket_id               VARCHAR2(100 CHAR) NOT NULL,
    bucket_name             VARCHAR2(255 CHAR),
    status                  VARCHAR2(30 CHAR) NOT NULL, -- PENDING, COMPLETED, REVERTED
    previous_step_id        VARCHAR2(36 CHAR),
    dependency_bucket_ids   VARCHAR2(1000 CHAR),
    resolved_by             VARCHAR2(100 CHAR),
    resolution_notes        VARCHAR2(2000 CHAR),
    circle_id               NUMBER(10,0),
    created_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    completed_at            TIMESTAMP(6),
    CONSTRAINT pk_revert_status PRIMARY KEY (revert_status_pk)
);

CREATE INDEX idx_revert_inst_id ON postpaid_workflow_revert_status(workflow_instance_id);
CREATE INDEX idx_revert_form_id ON postpaid_workflow_revert_status(form_id);
CREATE INDEX idx_revert_bucket_id ON postpaid_workflow_revert_status(bucket_id);
CREATE INDEX idx_revert_status ON postpaid_workflow_revert_status(status);


-- =============================================================================
-- SECTION 5: BUCKETS & TASK MANAGEMENT TABLES
-- =============================================================================

-- 9. POSTPAID WORKFLOW BUCKETS TABLE
CREATE TABLE postpaid_workflow_buckets (
    bucket_pk               VARCHAR2(36 CHAR) NOT NULL,
    bucket_id               VARCHAR2(100 CHAR) NOT NULL,
    name                    VARCHAR2(200 CHAR) NOT NULL,
    description             VARCHAR2(1000 CHAR),
    category                VARCHAR2(100 CHAR),
    priority                VARCHAR2(20 CHAR) NOT NULL, -- CRITICAL, HIGH, MEDIUM, LOW
    sla_hours               NUMBER(10,0),
    owner_group             VARCHAR2(200 CHAR),
    auto_actions            VARCHAR2(500 CHAR),
    active                  NUMBER(1,0) DEFAULT 1 NOT NULL,
    circle_id               NUMBER(10,0),
    possible_outcomes       VARCHAR2(2000 CHAR),
    created_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_bucket PRIMARY KEY (bucket_pk),
    CONSTRAINT uq_bucket_id UNIQUE (bucket_id),
    CONSTRAINT chk_bucket_active CHECK (active IN (0, 1))
);

CREATE UNIQUE INDEX idx_bucket_id ON postpaid_workflow_buckets(bucket_id);
CREATE INDEX idx_bucket_priority ON postpaid_workflow_buckets(priority);
CREATE INDEX idx_bucket_active ON postpaid_workflow_buckets(active);


-- 10. POSTPAID WORKFLOW BUCKET EXECUTIONS TABLE
CREATE TABLE postpaid_workflow_bucket_executions (
    bucket_execution_pk     VARCHAR2(36 CHAR) NOT NULL,
    execution_log_id        VARCHAR2(36 CHAR) NOT NULL,
    instance_id             VARCHAR2(36 CHAR),
    workflow_key            VARCHAR2(100 CHAR) NOT NULL,
    bucket_id               VARCHAR2(100 CHAR) NOT NULL,
    bucket_name             VARCHAR2(200 CHAR) NOT NULL,
    status                  VARCHAR2(20 CHAR) NOT NULL, -- PENDING, IN_REVIEW, RESOLVED
    priority                VARCHAR2(20 CHAR), -- CRITICAL, HIGH, MEDIUM, LOW
    circle_id               NUMBER(10,0),
    sla_hours               NUMBER(10,0),
    created_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    resolved_at             TIMESTAMP(6),
    resolved_by             VARCHAR2(200 CHAR),
    resolution_notes        VARCHAR2(2000 CHAR),
    CONSTRAINT pk_bucket_exec PRIMARY KEY (bucket_execution_pk),
    CONSTRAINT fk_bex_exec_log FOREIGN KEY (execution_log_id)
        REFERENCES postpaid_workflow_execution_logs(execution_log_pk) ON DELETE CASCADE,
    CONSTRAINT fk_bex_instance FOREIGN KEY (instance_id)
        REFERENCES postpaid_workflow_instances(workflow_instance_pk) ON DELETE CASCADE
);

CREATE INDEX idx_bex_bucket_id ON postpaid_workflow_bucket_executions(bucket_id);
CREATE INDEX idx_bex_status ON postpaid_workflow_bucket_executions(status);
CREATE INDEX idx_bex_workflow_key ON postpaid_workflow_bucket_executions(workflow_key);
CREATE INDEX idx_bex_exec_log_id ON postpaid_workflow_bucket_executions(execution_log_id);
CREATE INDEX idx_bex_created_at ON postpaid_workflow_bucket_executions(created_at);
CREATE INDEX idx_bex_instance_id ON postpaid_workflow_bucket_executions(instance_id);


-- =============================================================================
-- SECTION 6: CONTEXT CATALOG, RULES & INTEGRATIONS TABLES
-- =============================================================================

-- 11. POSTPAID WORKFLOW RULES TABLE
CREATE TABLE postpaid_workflow_rules (
    rule_pk                 VARCHAR2(36 CHAR) NOT NULL,
    rule_key                VARCHAR2(100 CHAR) NOT NULL,
    name                    VARCHAR2(200 CHAR) NOT NULL,
    description             VARCHAR2(1000 CHAR),
    expression              VARCHAR2(2000 CHAR) NOT NULL,
    active                  NUMBER(1,0) DEFAULT 1 NOT NULL,
    circle_id               NUMBER(10,0),
    created_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_rule PRIMARY KEY (rule_pk),
    CONSTRAINT uq_rule_key UNIQUE (rule_key),
    CONSTRAINT chk_rule_active CHECK (active IN (0, 1))
);

CREATE UNIQUE INDEX idx_rule_key ON postpaid_workflow_rules(rule_key);
CREATE INDEX idx_rule_active ON postpaid_workflow_rules(active);


-- 12. POSTPAID WORKFLOW CONTEXT SCHEMAS TABLE
CREATE TABLE postpaid_workflow_context_schemas (
    context_schema_pk       VARCHAR2(36 CHAR) NOT NULL,
    workflow_key            VARCHAR2(100 CHAR) NOT NULL,
    name                    VARCHAR2(200 CHAR) NOT NULL,
    description             VARCHAR2(1000 CHAR),
    context_id_field        VARCHAR2(100 CHAR),
    target_table            VARCHAR2(100 CHAR),
    target_pk_column        VARCHAR2(100 CHAR),
    target_status_column    VARCHAR2(100 CHAR),
    circle_id               NUMBER(10,0),
    created_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_ctx_schema PRIMARY KEY (context_schema_pk),
    CONSTRAINT uq_ctx_workflow_key UNIQUE (workflow_key)
);

CREATE UNIQUE INDEX idx_ctx_schema_key ON postpaid_workflow_context_schemas(workflow_key);


-- 13. POSTPAID WORKFLOW INTEGRATION REGISTRY TABLE
CREATE TABLE postpaid_workflow_integration_registry (
    integration_pk          VARCHAR2(36 CHAR) NOT NULL,
    integration_key         VARCHAR2(100 CHAR) NOT NULL,
    name                    VARCHAR2(200 CHAR) NOT NULL,
    provider_type           VARCHAR2(20 CHAR) NOT NULL, -- REST, DB, CONFIG
    endpoint_url            VARCHAR2(500 CHAR),
    method                  VARCHAR2(10 CHAR), -- GET, POST
    headers_json            VARCHAR2(2000 CHAR),
    request_template        VARCHAR2(2000 CHAR),
    timeout_ms              NUMBER(10,0) DEFAULT 5000,
    circle_id               NUMBER(10,0),
    created_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_integration PRIMARY KEY (integration_pk),
    CONSTRAINT uq_integration_key UNIQUE (integration_key)
);

CREATE UNIQUE INDEX idx_integration_key ON postpaid_workflow_integration_registry(integration_key);


-- 14. POSTPAID WORKFLOW CONTEXT FIELDS TABLE
CREATE TABLE postpaid_workflow_context_fields (
    context_field_pk        VARCHAR2(36 CHAR) NOT NULL,
    schema_id               VARCHAR2(36 CHAR) NOT NULL,
    field_key               VARCHAR2(100 CHAR) NOT NULL,
    display_name            VARCHAR2(200 CHAR),
    field_type              VARCHAR2(20 CHAR) NOT NULL, -- STRING, NUMBER, BOOLEAN, DATE
    required                NUMBER(1,0) DEFAULT 0 NOT NULL,
    default_value           VARCHAR2(500 CHAR),
    description             VARCHAR2(500 CHAR),
    field_order             NUMBER(10,0) NOT NULL,
    integration_id          VARCHAR2(36 CHAR),
    response_mapping        VARCHAR2(250 CHAR),
    cacheable               NUMBER(1,0) DEFAULT 1 NOT NULL,
    ttl_seconds             NUMBER(10,0) DEFAULT 300,
    cost                    VARCHAR2(20 CHAR) DEFAULT 'LOW' NOT NULL,
    expression              VARCHAR2(1000 CHAR),
    circle_id               NUMBER(10,0),
    CONSTRAINT pk_ctx_field PRIMARY KEY (context_field_pk),
    CONSTRAINT fk_field_schema FOREIGN KEY (schema_id) 
        REFERENCES postpaid_workflow_context_schemas(context_schema_pk) ON DELETE CASCADE,
    CONSTRAINT fk_ctx_field_integration FOREIGN KEY (integration_id)
        REFERENCES postpaid_workflow_integration_registry(integration_pk) ON DELETE SET NULL,
    CONSTRAINT chk_field_required CHECK (required IN (0, 1)),
    CONSTRAINT chk_field_cacheable CHECK (cacheable IN (0, 1))
);

CREATE INDEX idx_ctx_field_schema ON postpaid_workflow_context_fields(schema_id);
CREATE INDEX idx_ctx_field_key ON postpaid_workflow_context_fields(field_key);
CREATE INDEX idx_ctx_field_integration ON postpaid_workflow_context_fields(integration_id);


-- 15. POSTPAID WORKFLOW EVENT DEFINITIONS TABLE
CREATE TABLE postpaid_workflow_event_definitions (
    event_definition_pk     VARCHAR2(36 CHAR) NOT NULL,
    event_key               VARCHAR2(100 CHAR) NOT NULL,
    name                    VARCHAR2(200 CHAR) NOT NULL,
    description             VARCHAR2(1000 CHAR),
    kafka_topic             VARCHAR2(250 CHAR),
    correlation_key_path    VARCHAR2(250 CHAR),
    payload_schema          VARCHAR2(4000 CHAR),
    active                  NUMBER(1,0) DEFAULT 1 NOT NULL,
    circle_id               NUMBER(10,0),
    created_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_event_def PRIMARY KEY (event_definition_pk),
    CONSTRAINT uq_event_def_key UNIQUE (event_key),
    CONSTRAINT chk_event_active CHECK (active IN (0, 1))
);

CREATE UNIQUE INDEX idx_event_def_key ON postpaid_workflow_event_definitions(event_key);
CREATE INDEX idx_event_def_active ON postpaid_workflow_event_definitions(active);


-- 16. POSTPAID ONBOARD CAF TABLE (CUSTOMER / CAF DOMAIN)
CREATE TABLE POSTPAID_ONBOARD_CAF (
    caf_id                  NUMBER(19,0) NOT NULL,
    form_status             VARCHAR2(100 CHAR),
    circle_id               NUMBER(10,0),
    updated_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_postpaid_onboard_caf PRIMARY KEY (caf_id)
);

CREATE INDEX idx_poc_form_status ON POSTPAID_ONBOARD_CAF(form_status);


-- 17. POSTPAID WORKFLOW STAGED PAYLOADS TABLE (PRE-SUBMISSION / OUT-OF-ORDER STAGING)
CREATE TABLE postpaid_workflow_staged_payloads (
    staged_payload_pk       VARCHAR2(36 CHAR) NOT NULL,
    business_key            VARCHAR2(100 CHAR) NOT NULL,
    payload_type            VARCHAR2(50 CHAR) NOT NULL, -- DOCUMENTS, CAF, FAMILY_GROUP
    payload                 CLOB,
    status                  VARCHAR2(30 CHAR) DEFAULT 'STAGED' NOT NULL, -- STAGED, CONSUMED
    circle_id               NUMBER(10,0),
    created_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_staged_payload PRIMARY KEY (staged_payload_pk)
);

CREATE INDEX idx_staged_biz_key ON postpaid_workflow_staged_payloads(business_key);
CREATE INDEX idx_staged_type ON postpaid_workflow_staged_payloads(payload_type);
CREATE INDEX idx_staged_status ON postpaid_workflow_staged_payloads(status);


-- 18. POSTPAID ONBOARD COCP TABLE (ENTERPRISE / COCP DOMAIN)
CREATE TABLE POSTPAID_ONBOARD_COCP (
    cocp_id                 VARCHAR2(100 CHAR) NOT NULL,
    company_name            VARCHAR2(255 CHAR),
    form_status             VARCHAR2(100 CHAR),
    circle_id               NUMBER(10,0),
    updated_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_postpaid_onboard_cocp PRIMARY KEY (cocp_id)
);

CREATE INDEX idx_pocc_form_status ON POSTPAID_ONBOARD_COCP(form_status);

COMMIT;
-- =============================================================================
-- END OF ORACLE DDL SCRIPT
-- =============================================================================
