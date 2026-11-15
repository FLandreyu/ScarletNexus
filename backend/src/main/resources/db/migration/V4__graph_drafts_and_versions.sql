CREATE TABLE graph_drafts (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, skill_id UUID NOT NULL,
 draft_json JSONB NOT NULL CHECK(jsonb_typeof(draft_json)='object'),
 revision INT NOT NULL DEFAULT 0 CHECK(revision>=0), base_hash VARCHAR(64) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'EDITING' CHECK(status IN ('EDITING','PUBLISHED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(owner_id,skill_id) REFERENCES skills(owner_id,id), UNIQUE(owner_id,id)
);
CREATE INDEX graph_drafts_skill_idx ON graph_drafts(owner_id,skill_id,created_at);
CREATE TABLE knowledge_graph_versions (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, skill_id UUID NOT NULL,
 draft_id UUID NOT NULL UNIQUE, version_number INT NOT NULL CHECK(version_number>0),
 snapshot_json JSONB NOT NULL, published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(owner_id,skill_id) REFERENCES skills(owner_id,id),
 FOREIGN KEY(owner_id,draft_id) REFERENCES graph_drafts(owner_id,id),
 UNIQUE(skill_id,version_number), UNIQUE(owner_id,id)
);
CREATE TABLE graph_publish_keys (
 owner_id UUID NOT NULL REFERENCES users(id), key VARCHAR(128) NOT NULL,
 draft_id UUID NOT NULL, revision INT NOT NULL, version_id UUID NOT NULL,
 PRIMARY KEY(owner_id,key), FOREIGN KEY(owner_id,draft_id) REFERENCES graph_drafts(owner_id,id),
 FOREIGN KEY(owner_id,version_id) REFERENCES knowledge_graph_versions(owner_id,id)
);
CREATE FUNCTION reject_graph_version_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'published versions are immutable' USING ERRCODE='23514'; END;
$$;
CREATE TRIGGER graph_versions_immutable BEFORE UPDATE OR DELETE ON knowledge_graph_versions
 FOR EACH ROW EXECUTE FUNCTION reject_graph_version_mutation();
