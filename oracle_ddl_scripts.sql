-- =============================================================================
-- ATLAS WORKFLOW & DECISION ENGINE - ORACLE DATABASE DDL SCRIPT
-- =============================================================================
-- Target Database: Oracle Database 12c / 18c / 19c / 21c / 23c
-- Generated Date: 2026-08-20
-- Description: Complete production-ready DDL script including DROP statements,
--              TABLE definitions, PRIMARY KEYS, FOREIGN KEYS, UNIQUE CONSTRAINTS,
--              CHECK CONSTRAINTS, and INDEXES for Oracle DB.
-- =============================================================================

SET DEFINE OFF;
ALTER SESSION SET NLS_TIMESTAMP_FORMAT = 'YYYY-MM-DD HH24:MI:SS.FF';

-- =============================================================================
-- SECTION 1: CLEANUP / DROP TABLES (SAFE CASCADE DROPS)
-- =============================================================================

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE execution_log_details CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE execution_logs CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE revert_status CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE event_subscriptions CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE task_instances CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE bucket_executions CASCADE CONSTRAINTS';
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
    EXECUTE IMMEDIATE 'DROP TABLE context_fields CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE context_schemas CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE event_definitions CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE integration_registry CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE rules CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE buckets CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE customer_forms CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF;
END;
/


-- =============================================================================
-- SECTION 2: WORKFLOW CORE DEFINITION & VERSIONING TABLES
-- =============================================================================

-- 1. WORKFLOW DEFINITIONS TABLE
CREATE TABLE workflow_definitions (
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

CREATE UNIQUE INDEX idx_wf_def_key ON workflow_definitions(wf_key);


-- 2. WORKFLOW VERSIONS TABLE
CREATE TABLE workflow_versions (
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
        REFERENCES workflow_definitions(workflow_definition_pk) ON DELETE CASCADE
);

CREATE INDEX idx_wf_ver_status ON workflow_versions(status);


-- =============================================================================
-- SECTION 3: WORKFLOW RUNTIME EXECUTION & TASK TABLES
-- =============================================================================

-- 3. WORKFLOW INSTANCES TABLE
CREATE TABLE workflow_instances (
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
        REFERENCES workflow_versions(workflow_version_pk)
);

CREATE INDEX idx_inst_wf_key ON workflow_instances(workflow_key);
CREATE INDEX idx_inst_status ON workflow_instances(status);
CREATE INDEX idx_inst_created_at ON workflow_instances(created_at);
CREATE INDEX idx_inst_biz_key ON workflow_instances(business_key);


-- 4. TASK INSTANCES TABLE
CREATE TABLE task_instances (
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
        REFERENCES workflow_instances(workflow_instance_pk) ON DELETE CASCADE
);

CREATE INDEX idx_task_inst_parent ON task_instances(instance_id);
CREATE INDEX idx_task_inst_status ON task_instances(status);
CREATE INDEX idx_task_inst_type ON task_instances(task_type);


-- 5. EVENT SUBSCRIPTIONS TABLE
CREATE TABLE event_subscriptions (
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
        REFERENCES workflow_instances(workflow_instance_pk) ON DELETE CASCADE
);

CREATE INDEX idx_event_sub_bkey ON event_subscriptions(business_key);
CREATE INDEX idx_event_sub_type ON event_subscriptions(event_type);
CREATE INDEX idx_event_sub_status ON event_subscriptions(status);
CREATE INDEX idx_event_sub_instance ON event_subscriptions(instance_id);
CREATE INDEX idx_event_sub_lookup ON event_subscriptions(business_key, event_type, status);


-- =============================================================================
-- SECTION 4: AUDIT, LOGGING & REVERT TABLES (DECOUPLED FOR HIGH THROUGHPUT)
-- =============================================================================

-- 6. EXECUTION LOGS TABLE
CREATE TABLE execution_logs (
    execution_log_pk        VARCHAR2(36 CHAR) NOT NULL,
    workflow_key            VARCHAR2(100 CHAR) NOT NULL,
    version_id              VARCHAR2(36 CHAR),
    version_number          NUMBER(10,0),
    context_id              VARCHAR2(100 CHAR),
    instance_id             VARCHAR2(36 CHAR), -- Decoupled String ID reference
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
        REFERENCES workflow_versions(workflow_version_pk) ON DELETE SET NULL
);

CREATE INDEX idx_exec_wf_key ON execution_logs(workflow_key);
CREATE INDEX idx_exec_status ON execution_logs(status);
CREATE INDEX idx_exec_started_at ON execution_logs(started_at);
CREATE INDEX idx_exec_inst_id ON execution_logs(instance_id);


-- 7. EXECUTION LOG DETAILS TABLE
CREATE TABLE execution_log_details (
    execution_log_detail_pk VARCHAR2(36 CHAR) NOT NULL,
    execution_log_id        VARCHAR2(36 CHAR) NOT NULL,
    step_index              NUMBER(10,0) NOT NULL,
    node_id                 VARCHAR2(100 CHAR) NOT NULL,
    node_type               VARCHAR2(50 CHAR) NOT NULL,
    status                  VARCHAR2(30 CHAR) NOT NULL,
    input_context           CLOB,
    output_context          CLOB,
    duration_ms             NUMBER(19,0),
    timestamp               TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_exec_detail PRIMARY KEY (execution_log_detail_pk),
    CONSTRAINT fk_exec_detail_log FOREIGN KEY (execution_log_id) 
        REFERENCES execution_logs(execution_log_pk) ON DELETE CASCADE
);

CREATE INDEX idx_exec_detail_log ON execution_log_details(execution_log_id);


-- 8. REVERT STATUS TABLE
CREATE TABLE revert_status (
    revert_status_pk        VARCHAR2(36 CHAR) NOT NULL,
    workflow_instance_id    VARCHAR2(36 CHAR) NOT NULL, -- Decoupled String ID reference
    node_id                 VARCHAR2(100 CHAR) NOT NULL,
    revert_state            VARCHAR2(50 CHAR) NOT NULL,
    circle_id               NUMBER(10,0),
    updated_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_revert_status PRIMARY KEY (revert_status_pk)
);

CREATE INDEX idx_revert_inst_id ON revert_status(workflow_instance_id);


-- =============================================================================
-- SECTION 5: BUCKETS & TASK MANAGEMENT TABLES
-- =============================================================================

-- 9. BUCKETS TABLE
CREATE TABLE buckets (
    bucket_pk               VARCHAR2(36 CHAR) NOT NULL,
    bucket_id               VARCHAR2(100 CHAR) NOT NULL,
    name                    VARCHAR2(255 CHAR) NOT NULL,
    description             VARCHAR2(1000 CHAR),
    sla_hours               NUMBER(10,0),
    owner_group             VARCHAR2(100 CHAR),
    active                  NUMBER(1,0) DEFAULT 1 NOT NULL,
    circle_id               NUMBER(10,0),
    created_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_bucket PRIMARY KEY (bucket_pk),
    CONSTRAINT uq_bucket_id UNIQUE (bucket_id),
    CONSTRAINT chk_bucket_active CHECK (active IN (0, 1))
);

CREATE UNIQUE INDEX idx_bucket_id ON buckets(bucket_id);


-- 10. BUCKET EXECUTIONS TABLE
CREATE TABLE bucket_executions (
    bucket_execution_pk     VARCHAR2(36 CHAR) NOT NULL,
    bucket_id               VARCHAR2(100 CHAR) NOT NULL,
    workflow_instance_id    VARCHAR2(36 CHAR),
    business_key            VARCHAR2(100 CHAR),
    status                  VARCHAR2(30 CHAR) NOT NULL,
    outcome                 VARCHAR2(100 CHAR),
    assigned_to             VARCHAR2(100 CHAR),
    circle_id               NUMBER(10,0),
    input_context           CLOB,
    output_context          CLOB,
    created_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_bucket_exec PRIMARY KEY (bucket_execution_pk)
);

CREATE INDEX idx_bexec_bucket_id ON bucket_executions(bucket_id);
CREATE INDEX idx_bexec_inst_id ON bucket_executions(workflow_instance_id);
CREATE INDEX idx_bexec_bkey ON bucket_executions(business_key);
CREATE INDEX idx_bexec_status ON bucket_executions(status);


-- =============================================================================
-- SECTION 6: CONTEXT CATALOG, RULES & INTEGRATIONS TABLES
-- =============================================================================

-- 11. RULES TABLE
CREATE TABLE rules (
    rule_pk                 VARCHAR2(36 CHAR) NOT NULL,
    rule_id                 VARCHAR2(100 CHAR) NOT NULL,
    name                    VARCHAR2(255 CHAR) NOT NULL,
    description             VARCHAR2(1000 CHAR),
    expression              CLOB NOT NULL,
    active                  NUMBER(1,0) DEFAULT 1 NOT NULL,
    circle_id               NUMBER(10,0),
    created_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_rule PRIMARY KEY (rule_pk),
    CONSTRAINT uq_rule_id UNIQUE (rule_id),
    CONSTRAINT chk_rule_active CHECK (active IN (0, 1))
);

CREATE UNIQUE INDEX idx_rule_id ON rules(rule_id);


-- 12. CONTEXT SCHEMAS TABLE
CREATE TABLE context_schemas (
    context_schema_pk       VARCHAR2(36 CHAR) NOT NULL,
    name                    VARCHAR2(255 CHAR) NOT NULL,
    schema_key              VARCHAR2(100 CHAR) NOT NULL,
    description             VARCHAR2(1000 CHAR),
    circle_id               NUMBER(10,0),
    created_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_ctx_schema PRIMARY KEY (context_schema_pk),
    CONSTRAINT uq_ctx_schema_key UNIQUE (schema_key)
);

CREATE UNIQUE INDEX idx_ctx_schema_key ON context_schemas(schema_key);


-- 13. CONTEXT FIELDS TABLE
CREATE TABLE context_fields (
    context_field_pk        VARCHAR2(36 CHAR) NOT NULL,
    schema_id               VARCHAR2(36 CHAR) NOT NULL,
    field_key               VARCHAR2(100 CHAR) NOT NULL,
    display_name            VARCHAR2(255 CHAR) NOT NULL,
    data_type               VARCHAR2(50 CHAR) NOT NULL,
    provider_type           VARCHAR2(50 CHAR),
    provider_config         CLOB,
    circle_id               NUMBER(10,0),
    CONSTRAINT pk_ctx_field PRIMARY KEY (context_field_pk),
    CONSTRAINT fk_field_schema FOREIGN KEY (schema_id) 
        REFERENCES context_schemas(context_schema_pk) ON DELETE CASCADE
);

CREATE INDEX idx_ctx_field_schema ON context_fields(schema_id);
CREATE INDEX idx_ctx_field_key ON context_fields(field_key);


-- 14. EVENT DEFINITIONS TABLE
CREATE TABLE event_definitions (
    event_definition_pk     VARCHAR2(36 CHAR) NOT NULL,
    event_key               VARCHAR2(100 CHAR) NOT NULL,
    name                    VARCHAR2(255 CHAR) NOT NULL,
    description             VARCHAR2(1000 CHAR),
    payload_schema          CLOB,
    circle_id               NUMBER(10,0),
    created_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_event_def PRIMARY KEY (event_definition_pk),
    CONSTRAINT uq_event_def_key UNIQUE (event_key)
);

CREATE UNIQUE INDEX idx_event_def_key ON event_definitions(event_key);


-- 15. INTEGRATION REGISTRY TABLE
CREATE TABLE integration_registry (
    integration_pk          VARCHAR2(36 CHAR) NOT NULL,
    integration_key         VARCHAR2(100 CHAR) NOT NULL,
    name                    VARCHAR2(255 CHAR) NOT NULL,
    type                    VARCHAR2(50 CHAR) NOT NULL, -- REST, SOAP, DB, MQ
    config_json             CLOB NOT NULL,
    circle_id               NUMBER(10,0),
    active                  NUMBER(1,0) DEFAULT 1 NOT NULL,
    created_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_integration PRIMARY KEY (integration_pk),
    CONSTRAINT uq_integration_key UNIQUE (integration_key),
    CONSTRAINT chk_integration_active CHECK (active IN (0, 1))
);

CREATE UNIQUE INDEX idx_integration_key ON integration_registry(integration_key);


-- 16. CUSTOMER FORMS TABLE (SAMPLE / CAF DOMAIN)
CREATE TABLE customer_forms (
    caf_id                  VARCHAR2(100 CHAR) NOT NULL,
    msisdn                  VARCHAR2(50 CHAR),
    customer_type           VARCHAR2(50 CHAR),
    kyc_type                VARCHAR2(50 CHAR),
    status                  VARCHAR2(50 CHAR),
    form_data               CLOB,
    circle_id               NUMBER(10,0),
    created_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at              TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_customer_form PRIMARY KEY (caf_id)
);

CREATE INDEX idx_caf_msisdn ON customer_forms(msisdn);
CREATE INDEX idx_caf_status ON customer_forms(status);

COMMIT;
-- =============================================================================
-- END OF ORACLE DDL SCRIPT
-- =============================================================================
