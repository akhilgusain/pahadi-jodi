CREATE TABLE profile (
    id               UUID PRIMARY KEY,
    user_id          UUID NOT NULL,
    display_name     VARCHAR(100) NOT NULL,
    gender           VARCHAR(10)  NOT NULL,
    date_of_birth    DATE NOT NULL,
    community        VARCHAR(20)  NOT NULL,
    caste            VARCHAR(80),
    sub_caste        VARCHAR(80),
    gotra            VARCHAR(80),
    district         VARCHAR(30)  NOT NULL,
    village          VARCHAR(120),
    native_place     VARCHAR(160),
    mother_tongue    VARCHAR(30),
    education        VARCHAR(120),
    occupation       VARCHAR(120),
    bio              VARCHAR(2000),
    completeness     INT NOT NULL DEFAULT 0,
    version          BIGINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_profile_user UNIQUE (user_id)
);
CREATE INDEX idx_profile_district ON profile (district);
CREATE INDEX idx_profile_community ON profile (community);
