-- Birth data is sensitive personal data: date/time/place live only inside enc_payload (AES-GCM, app layer).
CREATE TABLE birth_profile (
    user_id      UUID PRIMARY KEY,
    enc_payload  TEXT NOT NULL,
    birth_hash   VARCHAR(64) NOT NULL,
    visibility   VARCHAR(20) NOT NULL,
    consent_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    version      BIGINT NOT NULL DEFAULT 0
);

-- Immutable chart per (birth_hash, ayanamsa, engine_version): safe to cache forever.
CREATE TABLE kundli (
    id              UUID PRIMARY KEY,
    birth_hash      VARCHAR(64) NOT NULL,
    ayanamsa        VARCHAR(20) NOT NULL,
    engine_version  VARCHAR(40) NOT NULL,
    lagna_rashi     SMALLINT,
    lagna_longitude DOUBLE PRECISION,
    moon_rashi      SMALLINT NOT NULL,
    moon_nakshatra  SMALLINT NOT NULL,
    moon_pada       SMALLINT NOT NULL,
    moon_degree     DOUBLE PRECISION NOT NULL,
    moon_longitude  DOUBLE PRECISION NOT NULL,
    mars_longitude  DOUBLE PRECISION NOT NULL,
    low_confidence  BOOLEAN NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_kundli UNIQUE (birth_hash, ayanamsa, engine_version)
);

CREATE TABLE user_kundli (
    user_id              UUID PRIMARY KEY,
    kundli_id            UUID NOT NULL REFERENCES kundli (id),
    manglik              VARCHAR(10) NOT NULL,
    mars_house_lagna     SMALLINT,
    mars_house_moon      SMALLINT NOT NULL,
    manglik_cancellations VARCHAR(300) NOT NULL DEFAULT '',
    computed_at          TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_user_kundli_manglik ON user_kundli (manglik);
