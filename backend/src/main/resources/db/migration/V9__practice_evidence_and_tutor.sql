ALTER TABLE learning_tasks ADD CONSTRAINT tasks_owner_node_unique UNIQUE(owner_id,id,node_id);
CREATE TABLE questions (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, node_id UUID NOT NULL,
 title VARCHAR(160) NOT NULL CHECK(length(btrim(title))>0), prompt TEXT NOT NULL CHECK(length(prompt) BETWEEN 1 AND 20000),
 question_type VARCHAR(16) NOT NULL CHECK(question_type IN ('EXACT_TEXT','PROOF','PROJECT')),
 answer_key TEXT NOT NULL CHECK(length(answer_key)<=20000), hint_text TEXT NOT NULL CHECK(length(hint_text)<=8000),
 status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','ARCHIVED')), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(owner_id,id), UNIQUE(owner_id,id,node_id), FOREIGN KEY(owner_id,node_id) REFERENCES knowledge_nodes(owner_id,id)
);
CREATE INDEX questions_node_idx ON questions(owner_id,node_id,created_at DESC,id);
CREATE TABLE question_exposures (
 owner_id UUID NOT NULL, question_id UUID NOT NULL, hint_level INT NOT NULL CHECK(hint_level BETWEEN 1 AND 2),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(owner_id,question_id), FOREIGN KEY(owner_id,question_id) REFERENCES questions(owner_id,id)
);
CREATE TABLE learning_attempts (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, task_id UUID NOT NULL, node_id UUID NOT NULL, question_id UUID NOT NULL,
 submitted_content TEXT NOT NULL CHECK(length(submitted_content)<=20000), independent BOOLEAN NOT NULL,
 hint_level_at_submission INT NOT NULL CHECK(hint_level_at_submission BETWEEN 0 AND 2),
 request_key VARCHAR(100) NOT NULL, input_hash VARCHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(owner_id,id), UNIQUE(owner_id,id,node_id), UNIQUE(owner_id,request_key),
 FOREIGN KEY(owner_id,task_id,node_id) REFERENCES learning_tasks(owner_id,id,node_id),
 FOREIGN KEY(owner_id,question_id,node_id) REFERENCES questions(owner_id,id,node_id)
);
CREATE INDEX attempts_task_idx ON learning_attempts(owner_id,task_id,created_at DESC,id);
CREATE TABLE attachments (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, task_id UUID NOT NULL,
 storage_key VARCHAR(80) NOT NULL UNIQUE, checksum VARCHAR(64) NOT NULL,
 size_bytes BIGINT NOT NULL CHECK(size_bytes BETWEEN 1 AND 20971520), width INT NOT NULL CHECK(width BETWEEN 1 AND 8000), height INT NOT NULL CHECK(height BETWEEN 1 AND 8000),
 status VARCHAR(16) NOT NULL CHECK(status IN ('QUARANTINED','READY','ARCHIVED')), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(owner_id,id), FOREIGN KEY(owner_id,task_id) REFERENCES learning_tasks(owner_id,id), CHECK(width::bigint*height<=16000000)
);
CREATE INDEX attachments_task_idx ON attachments(owner_id,task_id,created_at DESC,id);
CREATE TABLE attempt_attachments (
 owner_id UUID NOT NULL, attempt_id UUID NOT NULL, attachment_id UUID NOT NULL,
 PRIMARY KEY(owner_id,attempt_id,attachment_id), FOREIGN KEY(owner_id,attempt_id) REFERENCES learning_attempts(owner_id,id), FOREIGN KEY(owner_id,attachment_id) REFERENCES attachments(owner_id,id)
);
CREATE TABLE ai_conversations (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, task_id UUID NOT NULL, node_id UUID NOT NULL, question_id UUID,
 title VARCHAR(160) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(owner_id,id), FOREIGN KEY(owner_id,task_id,node_id) REFERENCES learning_tasks(owner_id,id,node_id), FOREIGN KEY(owner_id,question_id,node_id) REFERENCES questions(owner_id,id,node_id)
);
CREATE INDEX conversations_task_idx ON ai_conversations(owner_id,task_id,created_at DESC,id);
CREATE TABLE tutor_turns (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, conversation_id UUID NOT NULL, attempt_id UUID,
 request_key VARCHAR(100) NOT NULL, input_hash VARCHAR(64) NOT NULL, user_text TEXT NOT NULL CHECK(length(user_text) BETWEEN 1 AND 8000),
 mode VARCHAR(16) NOT NULL CHECK(mode IN ('EXPLAIN','SOCRATIC','FULL_SOLUTION')), context_json JSONB NOT NULL CHECK(jsonb_typeof(context_json)='object'),
 state VARCHAR(16) NOT NULL CHECK(state IN ('RUNNING','SUCCEEDED','FAILED')), assistant_text TEXT NOT NULL DEFAULT '', error_code VARCHAR(64),
 reserved_tokens BIGINT NOT NULL CHECK(reserved_tokens>0), input_tokens BIGINT CHECK(input_tokens>=0), output_tokens BIGINT CHECK(output_tokens>=0),
 budget_day DATE NOT NULL, currency VARCHAR(3) NOT NULL, provider_label VARCHAR(120) NOT NULL, model VARCHAR(120) NOT NULL,
 price_version VARCHAR(120) NOT NULL, price_date DATE NOT NULL, input_rate NUMERIC(24,12) NOT NULL CHECK(input_rate>=0), output_rate NUMERIC(24,12) NOT NULL CHECK(output_rate>=0),
 reserved_money NUMERIC(30,8) NOT NULL CHECK(reserved_money>0), charged_money NUMERIC(30,8), usage_source VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' CHECK(usage_source IN ('UNKNOWN','PRICED_USAGE','NOT_SENT')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), finished_at TIMESTAMPTZ,
 UNIQUE(owner_id,id), UNIQUE(owner_id,request_key), FOREIGN KEY(owner_id,conversation_id) REFERENCES ai_conversations(owner_id,id),
 FOREIGN KEY(owner_id,attempt_id) REFERENCES learning_attempts(owner_id,id), FOREIGN KEY(owner_id,budget_day) REFERENCES ai_daily_budgets(owner_id,budget_day),
 FOREIGN KEY(owner_id,budget_day,currency) REFERENCES ai_money_days(owner_id,budget_day,currency)
);
CREATE UNIQUE INDEX tutor_running_owner_idx ON tutor_turns(owner_id) WHERE state='RUNNING';
CREATE INDEX tutor_conversation_idx ON tutor_turns(owner_id,conversation_id,created_at DESC,id);
CREATE TABLE attempt_reviews (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, attempt_id UUID NOT NULL, tutor_turn_id UUID,
 verdict VARCHAR(16) NOT NULL CHECK(verdict IN ('CORRECT','INCORRECT','UNSURE')),
 reviewer_type VARCHAR(20) NOT NULL CHECK(reviewer_type IN ('RULE','SELF_REVIEW','REVIEWED_AI')), feedback TEXT NOT NULL CHECK(length(feedback)<=8000),
 request_key VARCHAR(100) NOT NULL, input_hash VARCHAR(64) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(owner_id,id), UNIQUE(owner_id,request_key), FOREIGN KEY(owner_id,attempt_id) REFERENCES learning_attempts(owner_id,id), FOREIGN KEY(owner_id,tutor_turn_id) REFERENCES tutor_turns(owner_id,id)
);
CREATE TABLE learning_evidence (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, node_id UUID NOT NULL, attempt_id UUID NOT NULL, review_id UUID NOT NULL,
 evidence_type VARCHAR(20) NOT NULL CHECK(evidence_type IN ('RULE_RESULT','SELF_REVIEW','REVIEWED_AI')),
 verification VARCHAR(20) NOT NULL CHECK(verification IN ('RULE_CHECKED','SELF_REPORTED')), outcome VARCHAR(16) NOT NULL CHECK(outcome IN ('CORRECT','INCORRECT','UNSURE')),
 independent BOOLEAN NOT NULL, observed_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(owner_id,id), UNIQUE(owner_id,id,node_id), UNIQUE(owner_id,review_id),
 FOREIGN KEY(owner_id,attempt_id,node_id) REFERENCES learning_attempts(owner_id,id,node_id), FOREIGN KEY(owner_id,review_id) REFERENCES attempt_reviews(owner_id,id)
);
CREATE INDEX evidence_node_idx ON learning_evidence(owner_id,node_id,observed_at DESC,id);
CREATE TABLE mastery_assessments (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, node_id UUID NOT NULL, level INT NOT NULL CHECK(level BETWEEN 0 AND 5),
 confidence VARCHAR(16) NOT NULL CHECK(confidence IN ('LOW','MEDIUM')), assessor_type VARCHAR(16) NOT NULL CHECK(assessor_type IN ('SELF','RULE')),
 reason TEXT NOT NULL CHECK(length(reason) BETWEEN 1 AND 8000), assessed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(owner_id,id,node_id), FOREIGN KEY(owner_id,node_id) REFERENCES knowledge_nodes(owner_id,id), CHECK(assessor_type<>'RULE' OR level<=2), CHECK(assessor_type<>'SELF' OR confidence='LOW')
);
CREATE INDEX assessments_node_idx ON mastery_assessments(owner_id,node_id,assessed_at DESC,id);
CREATE TABLE assessment_evidence (
 owner_id UUID NOT NULL, assessment_id UUID NOT NULL, node_id UUID NOT NULL, evidence_id UUID NOT NULL,
 PRIMARY KEY(owner_id,assessment_id,evidence_id), FOREIGN KEY(owner_id,assessment_id,node_id) REFERENCES mastery_assessments(owner_id,id,node_id), FOREIGN KEY(owner_id,evidence_id,node_id) REFERENCES learning_evidence(owner_id,id,node_id)
);
CREATE TRIGGER attempts_immutable BEFORE UPDATE OR DELETE ON learning_attempts FOR EACH ROW EXECUTE FUNCTION reject_learning_history_change();
CREATE TRIGGER reviews_immutable BEFORE UPDATE OR DELETE ON attempt_reviews FOR EACH ROW EXECUTE FUNCTION reject_learning_history_change();
CREATE TRIGGER evidence_immutable BEFORE UPDATE OR DELETE ON learning_evidence FOR EACH ROW EXECUTE FUNCTION reject_learning_history_change();
CREATE TRIGGER assessments_immutable BEFORE UPDATE OR DELETE ON mastery_assessments FOR EACH ROW EXECUTE FUNCTION reject_learning_history_change();
CREATE TRIGGER assessment_links_immutable BEFORE UPDATE OR DELETE ON assessment_evidence FOR EACH ROW EXECUTE FUNCTION reject_learning_history_change();
