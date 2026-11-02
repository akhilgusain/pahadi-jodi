-- Fact table. PII minimisation: only pseudonymous HMAC ids and coarse dimensions are stored, never raw payloads.
CREATE TABLE analytics_event (
    id           UUID PRIMARY KEY,
    event_id     VARCHAR(64)  NOT NULL,          -- envelope eventId (dedupe key)
    event_name   VARCHAR(40)  NOT NULL,          -- canonical name, e.g. REGISTERED, INTEREST_SENT, OTHER
    topic        VARCHAR(200) NOT NULL,
    user_hash    VARCHAR(64)  NOT NULL,          -- actor; '-' for system events
    target_hash  VARCHAR(64),                    -- the member acted upon (profile owner, interest recipient)
    occurred_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    event_day    DATE NOT NULL,                  -- business day in pj.analytics.timezone
    amount_minor BIGINT,                         -- payments: minor units (paise)
    currency     VARCHAR(3),
    plan         VARCHAR(30),
    community    VARCHAR(30),
    district     VARCHAR(40),
    received_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_event_actor UNIQUE (event_id, event_name, user_hash)
);
CREATE INDEX ix_event_name_day ON analytics_event (event_name, event_day);
CREATE INDEX ix_event_user ON analytics_event (user_hash, event_day);
CREATE INDEX ix_event_target ON analytics_event (target_hash, event_name, event_day);

-- Daily rollups written by the scheduled job; closed days are served from here.
CREATE TABLE daily_metric (
    metric_day   DATE NOT NULL,
    metric       VARCHAR(60) NOT NULL,
    metric_value BIGINT NOT NULL,
    computed_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (metric_day, metric)
);
