-- Append-only audit log, hash-chained: hash = sha256(prev_hash | row fields). audit_head pins the tail so truncation is detectable.
CREATE TABLE admin_audit_log (
    seq         BIGINT PRIMARY KEY,
    actor_id    VARCHAR(64)  NOT NULL,
    actor_roles VARCHAR(200),
    action      VARCHAR(60)  NOT NULL,
    target_type VARCHAR(40),
    target_id   VARCHAR(64),
    details     TEXT NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    prev_hash   VARCHAR(64) NOT NULL,
    hash        VARCHAR(64) NOT NULL
);
CREATE TABLE audit_head (
    id        INT PRIMARY KEY,
    last_seq  BIGINT NOT NULL,
    last_hash VARCHAR(64) NOT NULL
);
INSERT INTO audit_head (id, last_seq, last_hash) VALUES (1, 0, '0000000000000000000000000000000000000000000000000000000000000000');

CREATE TABLE user_restriction (
    user_id       UUID PRIMARY KEY,
    active        BOOLEAN NOT NULL,
    permanent     BOOLEAN NOT NULL,
    banned_until  TIMESTAMP WITH TIME ZONE,
    duration_days INT,
    reason        VARCHAR(500),
    imposed_by    VARCHAR(64),
    imposed_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE approval_request (
    id             UUID PRIMARY KEY,
    kind           VARCHAR(10) NOT NULL,
    target_user_id UUID NOT NULL,
    requested_by   VARCHAR(64) NOT NULL,
    reason         VARCHAR(500) NOT NULL,
    duration_days  INT,
    permanent      BOOLEAN NOT NULL,
    status         VARCHAR(12) NOT NULL,
    decided_by     VARCHAR(64),
    decision_note  VARCHAR(500),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at     TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_approval_status ON approval_request (status, created_at);
CREATE INDEX idx_approval_target ON approval_request (target_user_id, kind, status);

CREATE TABLE content_item (
    id           UUID PRIMARY KEY,
    content_type VARCHAR(10) NOT NULL,
    slug         VARCHAR(120) NOT NULL,
    locale       VARCHAR(8) NOT NULL,
    title        VARCHAR(200) NOT NULL,
    body         TEXT,
    image_url    VARCHAR(500),
    link_url     VARCHAR(500),
    sort_order   INT NOT NULL DEFAULT 0,
    status       VARCHAR(12) NOT NULL,
    created_by   VARCHAR(64),
    updated_by   VARCHAR(64),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_content UNIQUE (content_type, slug, locale)
);
CREATE INDEX idx_content_public ON content_item (content_type, status, sort_order);
