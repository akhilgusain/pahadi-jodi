CREATE TABLE media_asset (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL,
    kind          VARCHAR(12)  NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    declared_size BIGINT NOT NULL,
    size_bytes    BIGINT NOT NULL DEFAULT 0,
    status        VARCHAR(16)  NOT NULL,
    privacy       VARCHAR(16)  NOT NULL,
    storage_key   VARCHAR(300),
    thumb_key     VARCHAR(300),
    width         INT,
    height        INT,
    position      INT NOT NULL DEFAULT 0,
    is_primary    BOOLEAN NOT NULL DEFAULT FALSE,
    reject_reason VARCHAR(100),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_media_user ON media_asset (user_id, kind, status);

CREATE TABLE access_grant (
    id         UUID PRIMARY KEY,
    owner_id   UUID NOT NULL,
    viewer_id  UUID NOT NULL,
    status     VARCHAR(10) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_access_grant UNIQUE (owner_id, viewer_id)
);

-- Mutual-match links, populated from pj.match.created.v1 (both directions stored).
CREATE TABLE match_link (
    id         UUID PRIMARY KEY,
    user_a     UUID NOT NULL,
    user_b     UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_match_link UNIQUE (user_a, user_b)
);

CREATE TABLE verification_check (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL,
    type         VARCHAR(10) NOT NULL,
    status       VARCHAR(16) NOT NULL,
    provider_ref VARCHAR(100),
    id_hash      VARCHAR(64),
    id_last4     VARCHAR(8),
    score        DOUBLE PRECISION,
    reason       VARCHAR(200),
    attempts     INT NOT NULL DEFAULT 0,
    reviewed_by  UUID,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_check_user_type UNIQUE (user_id, type),
    CONSTRAINT uq_check_idhash UNIQUE (type, id_hash)
);

CREATE TABLE user_verification (
    user_id    UUID PRIMARY KEY,
    level      VARCHAR(12) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
