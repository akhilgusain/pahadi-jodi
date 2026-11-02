CREATE TABLE notification (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    type        VARCHAR(40) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        VARCHAR(1000) NOT NULL,
    data_json   TEXT,
    dedupe_key  VARCHAR(200) NOT NULL,
    inbox       BOOLEAN NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    read_at     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_notification_dedupe UNIQUE (user_id, dedupe_key)
);
CREATE INDEX idx_notification_inbox ON notification (user_id, inbox, created_at);

CREATE TABLE delivery_log (
    id                 UUID PRIMARY KEY,
    notification_id    UUID NOT NULL REFERENCES notification (id),
    user_id            UUID NOT NULL,
    channel            VARCHAR(12) NOT NULL,
    status             VARCHAR(12) NOT NULL,
    attempts           INT NOT NULL DEFAULT 0,
    next_attempt_at    TIMESTAMP WITH TIME ZONE,
    last_error         VARCHAR(500),
    suppressed_reason  VARCHAR(40),
    provider_ref       VARCHAR(100),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at            TIMESTAMP WITH TIME ZONE,
    version            BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_delivery_due ON delivery_log (status, next_attempt_at);
CREATE INDEX idx_delivery_user_channel ON delivery_log (user_id, channel, created_at);
CREATE INDEX idx_delivery_notification ON delivery_log (notification_id);

CREATE TABLE dead_letter (
    id               UUID PRIMARY KEY,
    delivery_id      UUID NOT NULL,
    notification_id  UUID NOT NULL,
    user_id          UUID NOT NULL,
    channel          VARCHAR(12) NOT NULL,
    payload          TEXT NOT NULL,
    error            VARCHAR(500),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE notification_preference (
    user_id           UUID PRIMARY KEY,
    in_app_enabled    BOOLEAN NOT NULL,
    email_enabled     BOOLEAN NOT NULL,
    sms_enabled       BOOLEAN NOT NULL,
    push_enabled      BOOLEAN NOT NULL,
    whatsapp_enabled  BOOLEAN NOT NULL,
    quiet_start       INT,
    quiet_end         INT,
    locale            VARCHAR(5) NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE device_token (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL,
    token         VARCHAR(512) NOT NULL,
    platform      VARCHAR(10) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_device_token UNIQUE (token)
);
CREATE INDEX idx_device_user ON device_token (user_id);
