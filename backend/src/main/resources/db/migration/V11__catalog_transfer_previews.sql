CREATE TABLE catalog_imports (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES users(id),
 document_json JSONB NOT NULL CHECK(jsonb_typeof(document_json)='object'),
 source_hash VARCHAR(64) NOT NULL, state VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK(state IN ('DRAFT','CONFIRMED')),
 revision INT NOT NULL DEFAULT 0 CHECK(revision>=0), request_key VARCHAR(100), result_json JSONB,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), confirmed_at TIMESTAMPTZ,
 UNIQUE(owner_id,id), UNIQUE(owner_id,request_key),
 CHECK((state='DRAFT' AND request_key IS NULL AND result_json IS NULL AND confirmed_at IS NULL)
 OR (state='CONFIRMED' AND request_key IS NOT NULL AND result_json IS NOT NULL AND confirmed_at IS NOT NULL))
);
CREATE INDEX catalog_imports_owner_time_idx ON catalog_imports(owner_id,created_at DESC,id);
CREATE FUNCTION protect_catalog_import_result() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR OLD.state='CONFIRMED' OR OLD.document_json<>NEW.document_json OR OLD.source_hash<>NEW.source_hash THEN
  RAISE EXCEPTION 'catalog import source and confirmed result are immutable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER catalog_import_result_immutable BEFORE UPDATE OR DELETE ON catalog_imports FOR EACH ROW EXECUTE FUNCTION protect_catalog_import_result();
