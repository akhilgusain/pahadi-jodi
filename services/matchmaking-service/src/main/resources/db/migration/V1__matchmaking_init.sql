-- Local projection of profile facets (fed by pj.profile.updated.v1); matchmaking never calls profile-service on the hot path.
CREATE TABLE profile_facet (
    profile_id    UUID PRIMARY KEY,
    user_id       UUID NOT NULL,
    display_name  VARCHAR(100),
    gender        VARCHAR(10),
    age           INT,
    community     VARCHAR(20),
    caste         VARCHAR(80),
    sub_caste     VARCHAR(80),
    gotra         VARCHAR(80),
    district      VARCHAR(30),
    division      VARCHAR(10),
    village       VARCHAR(120),
    education     VARCHAR(120),
    occupation    VARCHAR(120),
    mother_tongue VARCHAR(30),
    completeness  INT NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_facet_user UNIQUE (user_id)
);
CREATE INDEX idx_facet_gender ON profile_facet (gender);
CREATE INDEX idx_facet_district ON profile_facet (district);

CREATE TABLE partner_preference (
    user_id        UUID PRIMARY KEY,
    age_min        INT,
    age_max        INT,
    communities    VARCHAR(400),     -- CSV
    districts      VARCHAR(400),     -- CSV
    division       VARCHAR(10),
    educations     VARCHAR(400),     -- CSV of keywords/levels
    occupations    VARCHAR(400),     -- CSV of keywords
    mother_tongues VARCHAR(200),     -- CSV
    dealbreakers   VARCHAR(200),     -- CSV of AGE,COMMUNITY,DISTRICT,DIVISION,MOTHER_TONGUE,EDUCATION,OCCUPATION
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE interest (
    id              UUID PRIMARY KEY,
    from_user_id    UUID NOT NULL,
    to_user_id      UUID NOT NULL,
    from_profile_id UUID NOT NULL,
    to_profile_id   UUID NOT NULL,
    status          VARCHAR(12) NOT NULL,
    message         VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    responded_at    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_interest_pair UNIQUE (from_user_id, to_user_id)
);
CREATE INDEX idx_interest_to ON interest (to_user_id, status);
CREATE INDEX idx_interest_from ON interest (from_user_id, status);

CREATE TABLE match_record (
    id          UUID PRIMARY KEY,
    user_a      UUID NOT NULL,      -- always the smaller UUID, so a pair has one row
    user_b      UUID NOT NULL,
    profile_a   UUID NOT NULL,
    profile_b   UUID NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_match_pair UNIQUE (user_a, user_b)
);

CREATE TABLE shortlist_entry (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL,
    target_profile_id UUID NOT NULL,
    kind              VARCHAR(12) NOT NULL,   -- FAVORITE | IGNORE | BLOCK | PRIVATE
    list_name         VARCHAR(60) NOT NULL DEFAULT '',   -- only for PRIVATE
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_shortlist UNIQUE (user_id, target_profile_id, kind, list_name)
);
CREATE INDEX idx_shortlist_user_kind ON shortlist_entry (user_id, kind);

CREATE TABLE profile_note (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL,
    target_profile_id UUID NOT NULL,
    note              VARCHAR(2000) NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_note UNIQUE (user_id, target_profile_id)
);

CREATE TABLE daily_recommendation (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL,
    rec_date   DATE NOT NULL,
    profile_id UUID NOT NULL,
    rank_no    INT NOT NULL,
    score      DOUBLE PRECISION NOT NULL,
    reasons    VARCHAR(1000),
    CONSTRAINT uq_daily UNIQUE (user_id, rec_date, profile_id)
);
CREATE INDEX idx_daily_user_date ON daily_recommendation (user_id, rec_date);
