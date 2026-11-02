CREATE TABLE outbox_event (
    id            UUID PRIMARY KEY,
    topic         VARCHAR(200) NOT NULL,
    partition_key VARCHAR(200) NOT NULL,
    envelope      TEXT NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at  TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_outbox_unpublished ON outbox_event (published_at, created_at);
