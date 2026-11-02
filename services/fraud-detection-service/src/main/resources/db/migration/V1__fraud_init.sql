CREATE TABLE fraud_signal (
    id               UUID PRIMARY KEY,
    user_id          UUID NOT NULL,
    signal_type      VARCHAR(30) NOT NULL,
    device_hash      VARCHAR(128),
    ip               VARCHAR(64),
    user_agent       VARCHAR(300),
    email_domain     VARCHAR(255),
    phone_hash       VARCHAR(64),
    disposable_email BOOLEAN NOT NULL DEFAULT FALSE,
    counterpart_id   UUID,
    scam_hits        INT NOT NULL DEFAULT 0,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_signal_ip ON fraud_signal (ip, created_at);
CREATE INDEX idx_signal_device ON fraud_signal (device_hash);
CREATE INDEX idx_signal_user ON fraud_signal (user_id, signal_type, created_at);

CREATE TABLE fraud_rule (
    code        VARCHAR(40) PRIMARY KEY,
    description VARCHAR(300) NOT NULL,
    weight      INT NOT NULL,
    enabled     BOOLEAN NOT NULL,
    updated_by  VARCHAR(64),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
INSERT INTO fraud_rule (code, description, weight, enabled, updated_at) VALUES
 ('IP_VELOCITY', 'Several accounts created/active from one IP within an hour', 25, TRUE, CURRENT_TIMESTAMP),
 ('DEVICE_MULTI_ACCOUNT', 'One device fingerprint used by several accounts', 30, TRUE, CURRENT_TIMESTAMP),
 ('DISPOSABLE_EMAIL', 'Sign-up with a disposable e-mail domain', 20, TRUE, CURRENT_TIMESTAMP),
 ('DUPLICATE_PROFILE_TEXT', 'Profile text (near-)identical to another account', 25, TRUE, CURRENT_TIMESTAMP),
 ('DUPLICATE_PHOTO', 'Photo perceptually identical to another account''s photo', 35, TRUE, CURRENT_TIMESTAMP),
 ('SCAM_KEYWORDS', 'Romance-scam phrases in chat (money, crypto, gift cards, urgent travel)', 40, TRUE, CURRENT_TIMESTAMP),
 ('MESSAGE_BLAST', 'Messaging many distinct users within an hour', 30, TRUE, CURRENT_TIMESTAMP),
 ('PAYMENT_FAILURES', 'Repeated failed payment attempts (card testing)', 25, TRUE, CURRENT_TIMESTAMP),
 ('CHARGEBACK', 'Chargeback / payment dispute on the account', 40, TRUE, CURRENT_TIMESTAMP);

CREATE TABLE risk_evaluation (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL,
    score        INT NOT NULL,
    action       VARCHAR(30) NOT NULL,
    reasons      TEXT NOT NULL,
    trigger_type VARCHAR(30) NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_eval_user ON risk_evaluation (user_id, created_at);

CREATE TABLE user_risk (
    user_id       UUID PRIMARY KEY,
    score         INT NOT NULL,
    action        VARCHAR(30) NOT NULL,
    manual_action VARCHAR(30),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE fraud_case (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    status      VARCHAR(20) NOT NULL,
    score       INT NOT NULL,
    action      VARCHAR(30) NOT NULL,
    label       VARCHAR(20),
    notes       VARCHAR(1000),
    resolved_by UUID,
    resolved_at TIMESTAMP WITH TIME ZONE,
    opened_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_case_status ON fraud_case (status, opened_at);
CREATE INDEX idx_case_user ON fraud_case (user_id);

CREATE TABLE profile_fingerprint (
    user_id    UUID PRIMARY KEY,
    text_hash  VARCHAR(64) NOT NULL,
    simhash    BIGINT NOT NULL,
    band0      INT NOT NULL,
    band1      INT NOT NULL,
    band2      INT NOT NULL,
    band3      INT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_pf_hash ON profile_fingerprint (text_hash);
CREATE INDEX idx_pf_b0 ON profile_fingerprint (band0);
CREATE INDEX idx_pf_b1 ON profile_fingerprint (band1);
CREATE INDEX idx_pf_b2 ON profile_fingerprint (band2);
CREATE INDEX idx_pf_b3 ON profile_fingerprint (band3);

CREATE TABLE photo_hash (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL,
    phash      BIGINT NOT NULL,
    band0      INT NOT NULL,
    band1      INT NOT NULL,
    band2      INT NOT NULL,
    band3      INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_ph_user ON photo_hash (user_id);
CREATE INDEX idx_ph_b0 ON photo_hash (band0);
CREATE INDEX idx_ph_b1 ON photo_hash (band1);
CREATE INDEX idx_ph_b2 ON photo_hash (band2);
CREATE INDEX idx_ph_b3 ON photo_hash (band3);
