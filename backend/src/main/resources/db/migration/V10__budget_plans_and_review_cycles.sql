CREATE TABLE review_schedules (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, skill_id UUID NOT NULL, node_id UUID NOT NULL, original_task_id UUID NOT NULL, initial_evidence_id UUID,
 planned_minutes INT NOT NULL CHECK(planned_minutes BETWEEN 1 AND 1440), time_zone VARCHAR(80) NOT NULL,
 next_review_at TIMESTAMPTZ NOT NULL, interval_days INT NOT NULL CHECK(interval_days BETWEEN 1 AND 365),
 cycle_number INT NOT NULL DEFAULT 0 CHECK(cycle_number>=0), revision INT NOT NULL DEFAULT 0 CHECK(revision>=0),
 status VARCHAR(16) NOT NULL CHECK(status IN ('ACTIVE','PAUSED')), reason TEXT NOT NULL CHECK(length(reason) BETWEEN 1 AND 8000),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), last_completed_at TIMESTAMPTZ, updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(owner_id,id), UNIQUE(owner_id,id,node_id), UNIQUE(owner_id,node_id),
 FOREIGN KEY(owner_id,skill_id) REFERENCES skills(owner_id,id), FOREIGN KEY(owner_id,original_task_id,node_id) REFERENCES learning_tasks(owner_id,id,node_id),
 FOREIGN KEY(owner_id,initial_evidence_id,node_id) REFERENCES learning_evidence(owner_id,id,node_id)
);
CREATE INDEX reviews_due_idx ON review_schedules(owner_id,status,next_review_at,id);
CREATE TABLE review_events (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, schedule_id UUID NOT NULL,
 kind VARCHAR(16) NOT NULL CHECK(kind IN ('CREATE','ADJUST','RETEST')), request_key VARCHAR(100), input_hash VARCHAR(64),
 evidence_id UUID, attempt_id UUID, outcome VARCHAR(16) CHECK(outcome IN ('CORRECT','INCORRECT','UNSURE')), verification VARCHAR(20), independent BOOLEAN,
 previous_due TIMESTAMPTZ, next_due TIMESTAMPTZ NOT NULL, previous_interval INT, next_interval INT NOT NULL CHECK(next_interval BETWEEN 1 AND 365),
 reason TEXT NOT NULL CHECK(length(reason) BETWEEN 1 AND 8000), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(owner_id,request_key), UNIQUE(owner_id,evidence_id), FOREIGN KEY(owner_id,schedule_id) REFERENCES review_schedules(owner_id,id),
 FOREIGN KEY(owner_id,evidence_id) REFERENCES learning_evidence(owner_id,id), FOREIGN KEY(owner_id,attempt_id) REFERENCES learning_attempts(owner_id,id),
 CHECK((kind='RETEST')=(evidence_id IS NOT NULL AND attempt_id IS NOT NULL AND outcome IS NOT NULL AND request_key IS NOT NULL AND input_hash IS NOT NULL))
);
CREATE INDEX review_events_schedule_idx ON review_events(owner_id,schedule_id,created_at DESC,id);
CREATE TABLE review_cycle_tasks (
 owner_id UUID NOT NULL, schedule_id UUID NOT NULL, cycle_number INT NOT NULL CHECK(cycle_number>=0), node_id UUID NOT NULL, task_id UUID NOT NULL,
 PRIMARY KEY(owner_id,schedule_id,cycle_number), UNIQUE(owner_id,task_id),
 FOREIGN KEY(owner_id,schedule_id,node_id) REFERENCES review_schedules(owner_id,id,node_id), FOREIGN KEY(owner_id,task_id,node_id) REFERENCES learning_tasks(owner_id,id,node_id)
);
CREATE TABLE plan_drafts (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES users(id), plan_date DATE NOT NULL, time_zone VARCHAR(80) NOT NULL,
 budget_minutes INT NOT NULL CHECK(budget_minutes BETWEEN 0 AND 1440), source_hash VARCHAR(64) NOT NULL,
 revision INT NOT NULL DEFAULT 0 CHECK(revision>=0), state VARCHAR(16) NOT NULL CHECK(state IN ('DRAFT','CONFIRMED','CANCELLED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(owner_id,id)
);
CREATE INDEX plan_drafts_day_idx ON plan_drafts(owner_id,plan_date,created_at DESC,id);
CREATE TABLE plan_draft_limits (
 owner_id UUID NOT NULL, draft_id UUID NOT NULL, skill_id UUID NOT NULL, max_minutes INT NOT NULL CHECK(max_minutes BETWEEN 0 AND 1440),
 PRIMARY KEY(owner_id,draft_id,skill_id), FOREIGN KEY(owner_id,draft_id) REFERENCES plan_drafts(owner_id,id), FOREIGN KEY(owner_id,skill_id) REFERENCES skills(owner_id,id)
);
CREATE TABLE plan_draft_items (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, draft_id UUID NOT NULL, kind VARCHAR(16) NOT NULL CHECK(kind IN ('LEARN','REVIEW')), task_id UUID, review_id UUID,
 source_id UUID GENERATED ALWAYS AS (COALESCE(review_id,task_id)) STORED,
 skill_id UUID NOT NULL, node_id UUID NOT NULL, title VARCHAR(160) NOT NULL, minutes INT NOT NULL CHECK(minutes BETWEEN 1 AND 1440),
 task_revision INT, review_revision INT, eligible BOOLEAN NOT NULL, selected BOOLEAN NOT NULL, display_order INT NOT NULL CHECK(display_order>=0),
 reason TEXT NOT NULL CHECK(length(reason) BETWEEN 1 AND 8000), UNIQUE(owner_id,draft_id,kind,source_id),
 FOREIGN KEY(owner_id,draft_id) REFERENCES plan_drafts(owner_id,id), FOREIGN KEY(owner_id,task_id) REFERENCES learning_tasks(owner_id,id), FOREIGN KEY(owner_id,review_id) REFERENCES review_schedules(owner_id,id),
 FOREIGN KEY(owner_id,skill_id) REFERENCES skills(owner_id,id), FOREIGN KEY(owner_id,node_id) REFERENCES knowledge_nodes(owner_id,id),
 CHECK((kind='LEARN' AND task_id IS NOT NULL AND review_id IS NULL) OR (kind='REVIEW' AND review_id IS NOT NULL)), CHECK(NOT selected OR eligible)
);
CREATE TABLE daily_plans (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, draft_id UUID NOT NULL, plan_date DATE NOT NULL, time_zone VARCHAR(80) NOT NULL,
 version_number INT NOT NULL CHECK(version_number>0), budget_minutes INT NOT NULL CHECK(budget_minutes BETWEEN 0 AND 1440), confirmed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(owner_id,id), UNIQUE(owner_id,id,plan_date), UNIQUE(owner_id,plan_date,version_number), UNIQUE(owner_id,draft_id), FOREIGN KEY(owner_id,draft_id) REFERENCES plan_drafts(owner_id,id)
);
CREATE TABLE daily_plan_limits (
 owner_id UUID NOT NULL, plan_id UUID NOT NULL, skill_id UUID NOT NULL, max_minutes INT NOT NULL CHECK(max_minutes BETWEEN 0 AND 1440),
 PRIMARY KEY(owner_id,plan_id,skill_id), FOREIGN KEY(owner_id,plan_id) REFERENCES daily_plans(owner_id,id), FOREIGN KEY(owner_id,skill_id) REFERENCES skills(owner_id,id)
);
CREATE TABLE daily_plan_items (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, plan_id UUID NOT NULL, task_id UUID NOT NULL, review_id UUID, skill_id UUID NOT NULL,
 title_snapshot VARCHAR(160) NOT NULL, planned_minutes INT NOT NULL CHECK(planned_minutes BETWEEN 1 AND 1440), task_revision INT NOT NULL,
 display_order INT NOT NULL CHECK(display_order>=0), reason TEXT NOT NULL CHECK(length(reason) BETWEEN 1 AND 8000),
 UNIQUE(owner_id,plan_id,task_id), FOREIGN KEY(owner_id,plan_id) REFERENCES daily_plans(owner_id,id), FOREIGN KEY(owner_id,task_id) REFERENCES learning_tasks(owner_id,id),
 FOREIGN KEY(owner_id,review_id) REFERENCES review_schedules(owner_id,id), FOREIGN KEY(owner_id,skill_id) REFERENCES skills(owner_id,id)
);
CREATE TABLE daily_plan_days (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES users(id), plan_date DATE NOT NULL, active_plan_id UUID, revision INT NOT NULL DEFAULT 0 CHECK(revision>=0),
 UNIQUE(owner_id,plan_date), FOREIGN KEY(owner_id,active_plan_id,plan_date) REFERENCES daily_plans(owner_id,id,plan_date)
);
CREATE TABLE plan_confirmation_keys (
 owner_id UUID NOT NULL REFERENCES users(id), request_key VARCHAR(100) NOT NULL, input_hash VARCHAR(64) NOT NULL, plan_id UUID NOT NULL,
 PRIMARY KEY(owner_id,request_key), FOREIGN KEY(owner_id,plan_id) REFERENCES daily_plans(owner_id,id)
);
CREATE TABLE task_adjustments (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, task_id UUID NOT NULL, kind VARCHAR(16) NOT NULL CHECK(kind IN ('DEFER','CANCEL','SPLIT')),
 request_key VARCHAR(100) NOT NULL, input_hash VARCHAR(64) NOT NULL, previous_due DATE, next_due DATE, previous_status VARCHAR(16) NOT NULL, next_status VARCHAR(16) NOT NULL,
 previous_revision INT NOT NULL, next_revision INT NOT NULL, reason TEXT NOT NULL CHECK(length(reason) BETWEEN 1 AND 8000), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(owner_id,id), UNIQUE(owner_id,request_key), FOREIGN KEY(owner_id,task_id) REFERENCES learning_tasks(owner_id,id)
);
CREATE TABLE task_splits (
 owner_id UUID NOT NULL, adjustment_id UUID NOT NULL, parent_task_id UUID NOT NULL, child_task_id UUID NOT NULL,
 PRIMARY KEY(owner_id,adjustment_id,child_task_id), UNIQUE(owner_id,child_task_id), FOREIGN KEY(owner_id,adjustment_id) REFERENCES task_adjustments(owner_id,id),
 FOREIGN KEY(owner_id,parent_task_id) REFERENCES learning_tasks(owner_id,id), FOREIGN KEY(owner_id,child_task_id) REFERENCES learning_tasks(owner_id,id)
);
CREATE TRIGGER daily_plans_immutable BEFORE UPDATE OR DELETE ON daily_plans FOR EACH ROW EXECUTE FUNCTION reject_learning_history_change();
CREATE TRIGGER daily_plan_items_immutable BEFORE UPDATE OR DELETE ON daily_plan_items FOR EACH ROW EXECUTE FUNCTION reject_learning_history_change();
CREATE TRIGGER daily_plan_limits_immutable BEFORE UPDATE OR DELETE ON daily_plan_limits FOR EACH ROW EXECUTE FUNCTION reject_learning_history_change();
CREATE TRIGGER review_events_immutable BEFORE UPDATE OR DELETE ON review_events FOR EACH ROW EXECUTE FUNCTION reject_learning_history_change();
CREATE TRIGGER review_cycle_tasks_immutable BEFORE UPDATE OR DELETE ON review_cycle_tasks FOR EACH ROW EXECUTE FUNCTION reject_learning_history_change();
CREATE TRIGGER task_adjustments_immutable BEFORE UPDATE OR DELETE ON task_adjustments FOR EACH ROW EXECUTE FUNCTION reject_learning_history_change();
CREATE TRIGGER task_splits_immutable BEFORE UPDATE OR DELETE ON task_splits FOR EACH ROW EXECUTE FUNCTION reject_learning_history_change();
