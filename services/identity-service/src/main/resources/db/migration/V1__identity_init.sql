CREATE TABLE account (
    id            UUID PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_account_email UNIQUE (email)
);

CREATE TABLE refresh_token (
    id          UUID PRIMARY KEY,
    account_id  UUID NOT NULL REFERENCES account (id),
    family_id   UUID NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at     TIMESTAMP WITH TIME ZONE,
    revoked_at  TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_refresh_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_family ON refresh_token (family_id);
