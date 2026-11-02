CREATE TABLE saved_search (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL,
    name          VARCHAR(100) NOT NULL,
    criteria_json TEXT NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_saved_search_user ON saved_search (user_id);
