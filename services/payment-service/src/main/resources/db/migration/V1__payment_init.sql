CREATE TABLE payment_order (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL,
    plan_code           VARCHAR(20) NOT NULL,
    billing_period      VARCHAR(12) NOT NULL,
    provider            VARCHAR(10) NOT NULL,
    currency            VARCHAR(3)  NOT NULL,
    amount_minor        BIGINT NOT NULL,
    tax_minor           BIGINT NOT NULL,
    total_minor         BIGINT NOT NULL,
    refunded_minor      BIGINT NOT NULL DEFAULT 0,
    status              VARCHAR(24) NOT NULL,
    provider_order_id   VARCHAR(100) NOT NULL,
    provider_payment_id VARCHAR(100),
    method              VARCHAR(20),
    failure_code        VARCHAR(60),
    place_of_supply     VARCHAR(2) NOT NULL,
    idempotency_key     VARCHAR(100) NOT NULL,
    client_params       VARCHAR(4000),
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    captured_at         TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_order_provider_order UNIQUE (provider_order_id),
    CONSTRAINT uq_order_idem UNIQUE (user_id, idempotency_key)
);
CREATE INDEX idx_order_user ON payment_order (user_id, created_at);
CREATE INDEX idx_order_captured ON payment_order (provider, captured_at);

CREATE TABLE refund (
    id                 UUID PRIMARY KEY,
    order_id           UUID NOT NULL REFERENCES payment_order (id),
    provider_refund_id VARCHAR(100),
    amount_minor       BIGINT NOT NULL,
    reason             VARCHAR(30) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    requested_by       VARCHAR(100),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_refund_order ON refund (order_id);

CREATE TABLE invoice (
    id                 UUID PRIMARY KEY,
    invoice_no         VARCHAR(40) NOT NULL,
    order_id           UUID NOT NULL REFERENCES payment_order (id),
    user_id            UUID NOT NULL,
    type               VARCHAR(12) NOT NULL,
    related_invoice_id UUID,
    place_of_supply    VARCHAR(2) NOT NULL,
    hsn_sac            VARCHAR(10) NOT NULL,
    taxable_minor      BIGINT NOT NULL,
    cgst_minor         BIGINT NOT NULL DEFAULT 0,
    sgst_minor         BIGINT NOT NULL DEFAULT 0,
    igst_minor         BIGINT NOT NULL DEFAULT 0,
    total_minor        BIGINT NOT NULL,
    currency           VARCHAR(3) NOT NULL,
    issued_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_invoice_no UNIQUE (invoice_no)
);
CREATE INDEX idx_invoice_order ON invoice (order_id);

-- gap-free numbering per financial year (row-locked inside the capture transaction)
CREATE TABLE invoice_sequence (
    fy      VARCHAR(8) PRIMARY KEY,
    last_no BIGINT NOT NULL
);

CREATE TABLE mandate (
    id                   UUID PRIMARY KEY,
    user_id              UUID NOT NULL,
    provider             VARCHAR(10) NOT NULL,
    provider_mandate_id  VARCHAR(100) NOT NULL,
    plan_code            VARCHAR(20) NOT NULL,
    billing_period       VARCHAR(12) NOT NULL,
    method               VARCHAR(20) NOT NULL,
    status               VARCHAR(12) NOT NULL,
    max_amount_minor     BIGINT NOT NULL,
    currency             VARCHAR(3) NOT NULL,
    next_charge_at       TIMESTAMP WITH TIME ZONE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_mandate_provider UNIQUE (provider_mandate_id)
);
CREATE INDEX idx_mandate_user ON mandate (user_id);

-- idempotency: one row per provider event id ever received
CREATE TABLE webhook_event (
    id            VARCHAR(200) PRIMARY KEY,      -- provider ':' event id
    provider      VARCHAR(10) NOT NULL,
    event_type    VARCHAR(40) NOT NULL,
    result        VARCHAR(20) NOT NULL,
    process_error VARCHAR(200),
    payload       VARCHAR(8000),
    received_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE reconciliation_run (
    id               UUID PRIMARY KEY,
    provider         VARCHAR(10) NOT NULL,
    business_date    DATE NOT NULL,
    matched          INT NOT NULL,
    mismatched       INT NOT NULL,
    missing_local    INT NOT NULL,
    missing_provider INT NOT NULL,
    started_at       TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE reconciliation_item (
    id                  UUID PRIMARY KEY,
    run_id              UUID NOT NULL REFERENCES reconciliation_run (id),
    provider_payment_id VARCHAR(100),
    kind                VARCHAR(20) NOT NULL,
    local_amount        BIGINT,
    provider_amount     BIGINT,
    note                VARCHAR(300)
);
CREATE INDEX idx_recon_item_run ON reconciliation_item (run_id);
