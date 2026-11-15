CREATE TABLE ai_daily_budgets (
 owner_id UUID NOT NULL REFERENCES users(id), budget_day DATE NOT NULL,
 reserved_tokens BIGINT NOT NULL DEFAULT 0 CHECK(reserved_tokens>=0),
 consumed_tokens BIGINT NOT NULL DEFAULT 0 CHECK(consumed_tokens>=0),
 PRIMARY KEY(owner_id,budget_day)
);
CREATE TABLE ai_generation_jobs (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, skill_id UUID NOT NULL,
 request_key VARCHAR(128) NOT NULL, input_hash VARCHAR(64) NOT NULL,
 budget_day DATE NOT NULL, reserved_tokens BIGINT NOT NULL CHECK(reserved_tokens>0),
 state VARCHAR(16) NOT NULL CHECK(state IN ('RUNNING','SUCCEEDED','FAILED')),
 usage_source VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' CHECK(usage_source IN ('UNKNOWN','PROVIDER','NOT_SENT')),
 charged_tokens BIGINT CHECK(charged_tokens>=0), error_code VARCHAR(64),
 draft_id UUID NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), finished_at TIMESTAMPTZ,
 UNIQUE(owner_id,request_key), UNIQUE(owner_id,id),
 FOREIGN KEY(owner_id,skill_id) REFERENCES skills(owner_id,id),
 FOREIGN KEY(owner_id,draft_id) REFERENCES graph_drafts(owner_id,id),
 FOREIGN KEY(owner_id,budget_day) REFERENCES ai_daily_budgets(owner_id,budget_day)
);
CREATE INDEX ai_jobs_owner_created_idx ON ai_generation_jobs(owner_id,created_at DESC);
