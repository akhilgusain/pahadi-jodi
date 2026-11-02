-- Local read model of profiles, fed by pj.profile.updated.v1 (facets only, no contact data)
CREATE TABLE profile_projection (
    profile_id        UUID PRIMARY KEY,
    user_id           UUID NOT NULL,
    display_name      VARCHAR(100),
    gender            VARCHAR(10) NOT NULL,
    age               INT NOT NULL,
    community         VARCHAR(20),
    caste             VARCHAR(80),
    sub_caste         VARCHAR(80),
    gotra             VARCHAR(80),
    district          VARCHAR(30),
    division          VARCHAR(10),
    village           VARCHAR(120),
    education         VARCHAR(120),
    occupation        VARCHAR(120),
    mother_tongue     VARCHAR(30),
    completeness      INT NOT NULL DEFAULT 0,
    embedding         VARCHAR(8000),
    embedding_version VARCHAR(40),
    text_hash         VARCHAR(64),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_projection_user UNIQUE (user_id)
);
CREATE INDEX idx_projection_gender ON profile_projection (gender);

-- content-hash cache of LLM responses: sha256(promptKey | normalised redacted input)
CREATE TABLE llm_cache (
    cache_key  VARCHAR(64) PRIMARY KEY,
    prompt_key VARCHAR(60) NOT NULL,
    response   VARCHAR(8000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- per-user daily usage for the quota hook; id = userId ':' date
CREATE TABLE ai_usage (
    id         VARCHAR(60) PRIMARY KEY,
    user_id    UUID NOT NULL,
    usage_date DATE NOT NULL,
    calls      INT NOT NULL
);
