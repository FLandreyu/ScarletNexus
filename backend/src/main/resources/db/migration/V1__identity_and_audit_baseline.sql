CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT users_email_normalized CHECK (email = lower(btrim(email)) AND length(email) > 3),
    CONSTRAINT users_password_hash_nonempty CHECK (length(password_hash) >= 20),
    CONSTRAINT users_status_valid CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    entity_type VARCHAR(64) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(64) NOT NULL,
    before_json JSONB,
    after_json JSONB,
    request_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT audit_before_object CHECK (before_json IS NULL OR jsonb_typeof(before_json) = 'object'),
    CONSTRAINT audit_after_object CHECK (after_json IS NULL OR jsonb_typeof(after_json) = 'object')
);

CREATE INDEX audit_events_user_time_idx ON audit_events(user_id, created_at DESC);
CREATE INDEX audit_events_entity_time_idx ON audit_events(entity_type, entity_id, created_at DESC);

CREATE FUNCTION reject_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_events are append-only' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();
