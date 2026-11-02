CREATE TABLE plan (
    code            VARCHAR(20) PRIMARY KEY,           -- FREE | PREMIUM | PREMIUM_PLUS | VIP  (also the tier)
    name            VARCHAR(60) NOT NULL,
    tier_rank       INT NOT NULL,
    price_inr_minor BIGINT NOT NULL,                   -- paise, excl. 18% GST
    price_usd_minor BIGINT NOT NULL,                   -- cents (Stripe, diaspora)
    duration_days   INT NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE plan_entitlement (
    tier       VARCHAR(20) NOT NULL REFERENCES plan (code),
    ent_key    VARCHAR(60) NOT NULL,
    kind       VARCHAR(10) NOT NULL,                   -- QUOTA (consumed) | CAP (ceiling vs caller count) | FLAG
    enabled    BOOLEAN NOT NULL,
    quota_limit BIGINT,                                -- NULL = unlimited when enabled
    period_unit VARCHAR(10) NOT NULL,                  -- DAY | MONTH | NONE
    variant    VARCHAR(40),                            -- descriptor for flag-style entitlements
    PRIMARY KEY (tier, ent_key)
);

CREATE TABLE subscription (
    id                    UUID PRIMARY KEY,
    user_id               UUID NOT NULL,
    plan_code             VARCHAR(20) NOT NULL REFERENCES plan (code),
    status                VARCHAR(10) NOT NULL,        -- PENDING | ACTIVE | GRACE | EXPIRED | CANCELED
    started_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    current_period_end    TIMESTAMP WITH TIME ZONE NOT NULL,
    grace_ends_at         TIMESTAMP WITH TIME ZONE,
    cancel_at_period_end  BOOLEAN NOT NULL DEFAULT FALSE,
    last_payment_id       VARCHAR(80),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_subscription_user UNIQUE (user_id)   -- one current subscription row per user
);
CREATE INDEX idx_subscription_status_end ON subscription (status, current_period_end);
CREATE INDEX idx_subscription_grace ON subscription (status, grace_ends_at);

CREATE TABLE applied_payment (                          -- idempotency for payment events
    payment_id VARCHAR(80) PRIMARY KEY,
    user_id    UUID NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE usage_counter (
    id         VARCHAR(160) PRIMARY KEY,               -- userId:key:periodKey
    user_id    UUID NOT NULL,
    ent_key    VARCHAR(60) NOT NULL,
    period_key VARCHAR(12) NOT NULL,                   -- yyyyMMdd | yyyyMM | ALL
    used       BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_usage_user ON usage_counter (user_id, period_key);

INSERT INTO plan (code, name, tier_rank, price_inr_minor, price_usd_minor, duration_days) VALUES
 ('FREE',         'Free',          0,       0,     0,   36500),
 ('PREMIUM',      'Premium',       1,  149900,  2900,      90),
 ('PREMIUM_PLUS', 'Premium Plus',  2,  299900,  5900,     180),
 ('VIP',          'VIP',           3, 1499900, 24900,     365);

INSERT INTO plan_entitlement (tier, ent_key, kind, enabled, quota_limit, period_unit, variant) VALUES
 ('FREE', 'search.results_per_day', 'QUOTA', TRUE, 50, 'DAY', NULL),
 ('PREMIUM', 'search.results_per_day', 'QUOTA', TRUE, NULL, 'DAY', NULL),
 ('PREMIUM_PLUS', 'search.results_per_day', 'QUOTA', TRUE, NULL, 'DAY', NULL),
 ('VIP', 'search.results_per_day', 'QUOTA', TRUE, NULL, 'DAY', NULL),
 ('FREE', 'search.advanced_filters', 'FLAG', TRUE, NULL, 'NONE', 'BASIC_5'),
 ('PREMIUM', 'search.advanced_filters', 'FLAG', TRUE, NULL, 'NONE', 'ALL'),
 ('PREMIUM_PLUS', 'search.advanced_filters', 'FLAG', TRUE, NULL, 'NONE', 'ALL'),
 ('VIP', 'search.advanced_filters', 'FLAG', TRUE, NULL, 'NONE', 'ALL'),
 ('FREE', 'search.saved_searches', 'CAP', TRUE, 1, 'NONE', NULL),
 ('PREMIUM', 'search.saved_searches', 'CAP', TRUE, 5, 'NONE', NULL),
 ('PREMIUM_PLUS', 'search.saved_searches', 'CAP', TRUE, 15, 'NONE', NULL),
 ('VIP', 'search.saved_searches', 'CAP', TRUE, 30, 'NONE', NULL),
 ('FREE', 'profile.views_per_day', 'QUOTA', TRUE, 10, 'DAY', NULL),
 ('PREMIUM', 'profile.views_per_day', 'QUOTA', TRUE, 100, 'DAY', NULL),
 ('PREMIUM_PLUS', 'profile.views_per_day', 'QUOTA', TRUE, 300, 'DAY', NULL),
 ('VIP', 'profile.views_per_day', 'QUOTA', TRUE, 1000, 'DAY', NULL),
 ('FREE', 'contact.unlocks_per_month', 'QUOTA', FALSE, 0, 'MONTH', NULL),
 ('PREMIUM', 'contact.unlocks_per_month', 'QUOTA', TRUE, 30, 'MONTH', NULL),
 ('PREMIUM_PLUS', 'contact.unlocks_per_month', 'QUOTA', TRUE, 75, 'MONTH', NULL),
 ('VIP', 'contact.unlocks_per_month', 'QUOTA', TRUE, 150, 'MONTH', NULL),
 ('FREE', 'interest.send_per_day', 'QUOTA', TRUE, 5, 'DAY', NULL),
 ('PREMIUM', 'interest.send_per_day', 'QUOTA', TRUE, 30, 'DAY', NULL),
 ('PREMIUM_PLUS', 'interest.send_per_day', 'QUOTA', TRUE, 60, 'DAY', NULL),
 ('VIP', 'interest.send_per_day', 'QUOTA', TRUE, 100, 'DAY', NULL),
 ('FREE', 'chat.after_mutual_interest', 'QUOTA', TRUE, 5, 'DAY', NULL),
 ('PREMIUM', 'chat.after_mutual_interest', 'QUOTA', TRUE, NULL, 'DAY', NULL),
 ('PREMIUM_PLUS', 'chat.after_mutual_interest', 'QUOTA', TRUE, NULL, 'DAY', NULL),
 ('VIP', 'chat.after_mutual_interest', 'QUOTA', TRUE, NULL, 'DAY', NULL),
 ('FREE', 'chat.initiate', 'QUOTA', FALSE, 0, 'DAY', NULL),
 ('PREMIUM', 'chat.initiate', 'QUOTA', TRUE, 10, 'DAY', NULL),
 ('PREMIUM_PLUS', 'chat.initiate', 'QUOTA', TRUE, 25, 'DAY', NULL),
 ('VIP', 'chat.initiate', 'QUOTA', TRUE, 50, 'DAY', NULL),
 ('FREE', 'chat.voice_notes', 'FLAG', FALSE, NULL, 'NONE', 'RECEIVE_ONLY'),
 ('PREMIUM', 'chat.voice_notes', 'FLAG', TRUE, NULL, 'NONE', 'SEND'),
 ('PREMIUM_PLUS', 'chat.voice_notes', 'FLAG', TRUE, NULL, 'NONE', 'SEND'),
 ('VIP', 'chat.voice_notes', 'FLAG', TRUE, NULL, 'NONE', 'SEND'),
 ('FREE', 'chat.translation_per_day', 'QUOTA', FALSE, 0, 'DAY', NULL),
 ('PREMIUM', 'chat.translation_per_day', 'QUOTA', TRUE, 50, 'DAY', NULL),
 ('PREMIUM_PLUS', 'chat.translation_per_day', 'QUOTA', TRUE, NULL, 'DAY', NULL),
 ('VIP', 'chat.translation_per_day', 'QUOTA', TRUE, NULL, 'DAY', NULL),
 ('FREE', 'call.video_audio_minutes_per_month', 'QUOTA', FALSE, 0, 'MONTH', NULL),
 ('PREMIUM', 'call.video_audio_minutes_per_month', 'QUOTA', TRUE, 120, 'MONTH', NULL),
 ('PREMIUM_PLUS', 'call.video_audio_minutes_per_month', 'QUOTA', TRUE, 400, 'MONTH', NULL),
 ('VIP', 'call.video_audio_minutes_per_month', 'QUOTA', TRUE, NULL, 'MONTH', NULL),
 ('FREE', 'ai.recommendations_per_day', 'QUOTA', TRUE, 3, 'DAY', NULL),
 ('PREMIUM', 'ai.recommendations_per_day', 'QUOTA', TRUE, 10, 'DAY', NULL),
 ('PREMIUM_PLUS', 'ai.recommendations_per_day', 'QUOTA', TRUE, 25, 'DAY', NULL),
 ('VIP', 'ai.recommendations_per_day', 'QUOTA', TRUE, 40, 'DAY', NULL),
 ('FREE', 'ai.compatibility_report_per_month', 'QUOTA', FALSE, 0, 'MONTH', NULL),
 ('PREMIUM', 'ai.compatibility_report_per_month', 'QUOTA', TRUE, 3, 'MONTH', NULL),
 ('PREMIUM_PLUS', 'ai.compatibility_report_per_month', 'QUOTA', TRUE, 15, 'MONTH', NULL),
 ('VIP', 'ai.compatibility_report_per_month', 'QUOTA', TRUE, NULL, 'MONTH', NULL),
 ('FREE', 'ai.profile_assistant_per_month', 'QUOTA', TRUE, 3, 'MONTH', NULL),
 ('PREMIUM', 'ai.profile_assistant_per_month', 'QUOTA', TRUE, 15, 'MONTH', NULL),
 ('PREMIUM_PLUS', 'ai.profile_assistant_per_month', 'QUOTA', TRUE, NULL, 'MONTH', NULL),
 ('VIP', 'ai.profile_assistant_per_month', 'QUOTA', TRUE, NULL, 'MONTH', NULL),
 ('FREE', 'ai.icebreakers_per_day', 'QUOTA', FALSE, 0, 'DAY', NULL),
 ('PREMIUM', 'ai.icebreakers_per_day', 'QUOTA', TRUE, 10, 'DAY', NULL),
 ('PREMIUM_PLUS', 'ai.icebreakers_per_day', 'QUOTA', TRUE, 40, 'DAY', NULL),
 ('VIP', 'ai.icebreakers_per_day', 'QUOTA', TRUE, 100, 'DAY', NULL),
 ('FREE', 'analytics.profile_insights', 'FLAG', TRUE, NULL, 'NONE', 'COUNTS_ONLY'),
 ('PREMIUM', 'analytics.profile_insights', 'FLAG', TRUE, NULL, 'NONE', 'VIEWERS_7D'),
 ('PREMIUM_PLUS', 'analytics.profile_insights', 'FLAG', TRUE, NULL, 'NONE', 'VIEWERS_90D_TRENDS'),
 ('VIP', 'analytics.profile_insights', 'FLAG', TRUE, NULL, 'NONE', 'VIEWERS_365D_EXPORT'),
 ('FREE', 'visibility.boost_per_month', 'QUOTA', FALSE, 0, 'MONTH', NULL),
 ('PREMIUM', 'visibility.boost_per_month', 'QUOTA', FALSE, 0, 'MONTH', NULL),
 ('PREMIUM_PLUS', 'visibility.boost_per_month', 'QUOTA', TRUE, 2, 'MONTH', NULL),
 ('VIP', 'visibility.boost_per_month', 'QUOTA', TRUE, 4, 'MONTH', NULL),
 ('FREE', 'ads', 'FLAG', TRUE, NULL, 'NONE', NULL),
 ('PREMIUM', 'ads', 'FLAG', FALSE, NULL, 'NONE', NULL),
 ('PREMIUM_PLUS', 'ads', 'FLAG', FALSE, NULL, 'NONE', NULL),
 ('VIP', 'ads', 'FLAG', FALSE, NULL, 'NONE', NULL),
 ('FREE', 'support.relationship_manager', 'FLAG', FALSE, NULL, 'NONE', NULL),
 ('PREMIUM', 'support.relationship_manager', 'FLAG', FALSE, NULL, 'NONE', NULL),
 ('PREMIUM_PLUS', 'support.relationship_manager', 'FLAG', FALSE, NULL, 'NONE', NULL),
 ('VIP', 'support.relationship_manager', 'FLAG', TRUE, NULL, 'NONE', 'DEDICATED_RM'),
 ('FREE', 'exclusive_matches', 'FLAG', FALSE, NULL, 'NONE', NULL),
 ('PREMIUM', 'exclusive_matches', 'FLAG', FALSE, NULL, 'NONE', NULL),
 ('PREMIUM_PLUS', 'exclusive_matches', 'FLAG', FALSE, NULL, 'NONE', NULL),
 ('VIP', 'exclusive_matches', 'FLAG', TRUE, NULL, 'NONE', NULL),
 ('FREE', 'verified_badge_priority', 'FLAG', TRUE, NULL, 'NONE', 'NORMAL'),
 ('PREMIUM', 'verified_badge_priority', 'FLAG', TRUE, NULL, 'NONE', 'NORMAL'),
 ('PREMIUM_PLUS', 'verified_badge_priority', 'FLAG', TRUE, NULL, 'NONE', 'PRIORITY_24H'),
 ('VIP', 'verified_badge_priority', 'FLAG', TRUE, NULL, 'NONE', 'PRIORITY_4H');
