CREATE TABLE app_user (
    id                     UUID PRIMARY KEY,          -- = identity-service account id (JWT sub)
    status                 VARCHAR(20) NOT NULL,      -- ACTIVE | DEACTIVATED | PENDING_DELETION | DELETED
    deletion_scheduled_at  TIMESTAMP WITH TIME ZONE,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE contact (
    user_id        UUID PRIMARY KEY REFERENCES app_user (id),
    email          VARCHAR(320),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    phone          VARCHAR(20),                       -- E.164; encrypt at rest in prod (KMS), masked in API
    phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE preference (
    user_id               UUID PRIMARY KEY REFERENCES app_user (id),
    language              VARCHAR(5)  NOT NULL DEFAULT 'en',
    notify_email          BOOLEAN NOT NULL DEFAULT TRUE,
    notify_sms            BOOLEAN NOT NULL DEFAULT TRUE,
    notify_push           BOOLEAN NOT NULL DEFAULT TRUE,
    notify_whatsapp       BOOLEAN NOT NULL DEFAULT FALSE,
    profile_visibility    VARCHAR(20) NOT NULL DEFAULT 'ALL_MEMBERS',  -- ALL_MEMBERS | VERIFIED_ONLY | CONTACTS_ONLY
    allow_direct_messages VARCHAR(20) NOT NULL DEFAULT 'PREMIUM',      -- NONE | PREMIUM | ALL
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE consent (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES app_user (id),
    type        VARCHAR(20) NOT NULL,                 -- TERMS | PRIVACY | MARKETING
    version     VARCHAR(20) NOT NULL,
    granted     BOOLEAN NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_consent_user ON consent (user_id, type, recorded_at);

CREATE TABLE device (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES app_user (id),
    name       VARCHAR(120) NOT NULL,
    platform   VARCHAR(20) NOT NULL,                  -- WEB | ANDROID | IOS
    last_seen  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_device_user ON device (user_id);

CREATE TABLE data_request (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL REFERENCES app_user (id),
    type          VARCHAR(10) NOT NULL,               -- EXPORT | ERASURE
    status        VARCHAR(12) NOT NULL,               -- REQUESTED | CANCELED | COMPLETED
    requested_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_for TIMESTAMP WITH TIME ZONE,           -- erasure: end of grace period
    completed_at  TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_data_request_user ON data_request (user_id, type, status);
CREATE INDEX idx_data_request_due ON data_request (status, scheduled_for);
