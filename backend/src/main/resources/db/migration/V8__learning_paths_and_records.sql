CREATE TABLE learning_goals (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, skill_id UUID NOT NULL,
 title VARCHAR(160) NOT NULL CHECK(length(btrim(title))>0), target_description TEXT NOT NULL,
 deadline DATE, priority INT NOT NULL CHECK(priority BETWEEN 1 AND 5),
 status VARCHAR(16) NOT NULL CHECK(status IN ('ACTIVE','COMPLETED','ARCHIVED')),
 revision INT NOT NULL DEFAULT 0 CHECK(revision>=0), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(owner_id,id), UNIQUE(owner_id,id,skill_id), FOREIGN KEY(owner_id,skill_id) REFERENCES skills(owner_id,id)
);
CREATE INDEX goals_owner_idx ON learning_goals(owner_id,updated_at DESC,id);
CREATE TABLE learning_paths (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, skill_id UUID NOT NULL, goal_id UUID,
 family_id UUID NOT NULL, version_number INT NOT NULL CHECK(version_number>0), previous_version_id UUID,
 title VARCHAR(160) NOT NULL CHECK(length(btrim(title))>0), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(owner_id,id), UNIQUE(owner_id,family_id,version_number),
 FOREIGN KEY(owner_id,skill_id) REFERENCES skills(owner_id,id),
 FOREIGN KEY(owner_id,goal_id,skill_id) REFERENCES learning_goals(owner_id,id,skill_id),
 FOREIGN KEY(owner_id,previous_version_id) REFERENCES learning_paths(owner_id,id)
);
CREATE INDEX paths_owner_idx ON learning_paths(owner_id,created_at DESC,id);
CREATE TABLE learning_path_stages (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, path_id UUID NOT NULL, title VARCHAR(160) NOT NULL CHECK(length(btrim(title))>0),
 display_order INT NOT NULL CHECK(display_order>=0), UNIQUE(owner_id,id,path_id), UNIQUE(path_id,display_order),
 FOREIGN KEY(owner_id,path_id) REFERENCES learning_paths(owner_id,id)
);
CREATE TABLE learning_path_nodes (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, path_id UUID NOT NULL, stage_id UUID NOT NULL, node_id UUID NOT NULL,
 title_snapshot VARCHAR(160) NOT NULL, display_order INT NOT NULL CHECK(display_order>=0),
 target_level INT NOT NULL CHECK(target_level BETWEEN 0 AND 5), required BOOLEAN NOT NULL,
 UNIQUE(owner_id,id), UNIQUE(owner_id,id,path_id), UNIQUE(path_id,node_id), UNIQUE(stage_id,display_order),
 FOREIGN KEY(owner_id,stage_id,path_id) REFERENCES learning_path_stages(owner_id,id,path_id),
 FOREIGN KEY(owner_id,node_id) REFERENCES knowledge_nodes(owner_id,id)
);
CREATE TABLE path_enrollments (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, path_id UUID NOT NULL,
 status VARCHAR(16) NOT NULL CHECK(status IN ('ACTIVE','PAUSED','COMPLETED')),
 started_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(owner_id,id), UNIQUE(owner_id,id,path_id), UNIQUE(owner_id,path_id),
 FOREIGN KEY(owner_id,path_id) REFERENCES learning_paths(owner_id,id)
);
CREATE UNIQUE INDEX enrollment_active_owner_idx ON path_enrollments(owner_id) WHERE status='ACTIVE';
CREATE TABLE learning_progress (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, enrollment_id UUID NOT NULL, path_id UUID NOT NULL, entry_id UUID NOT NULL,
 status VARCHAR(16) NOT NULL CHECK(status IN ('NOT_STARTED','IN_PROGRESS','COMPLETED')),
 revision INT NOT NULL DEFAULT 0 CHECK(revision>=0), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(owner_id,enrollment_id,entry_id),
 FOREIGN KEY(owner_id,enrollment_id,path_id) REFERENCES path_enrollments(owner_id,id,path_id),
 FOREIGN KEY(owner_id,entry_id,path_id) REFERENCES learning_path_nodes(owner_id,id,path_id)
);
CREATE TABLE learning_tasks (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, skill_id UUID NOT NULL, goal_id UUID, node_id UUID NOT NULL,
 enrollment_id UUID, entry_id UUID, path_id UUID,
 title VARCHAR(160) NOT NULL CHECK(length(btrim(title))>0), planned_minutes INT NOT NULL CHECK(planned_minutes BETWEEN 1 AND 1440),
 due_date DATE, status VARCHAR(16) NOT NULL CHECK(status IN ('TODO','DOING','DONE','DEFERRED','CANCELLED')),
 revision INT NOT NULL DEFAULT 0 CHECK(revision>=0), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(owner_id,id),
 FOREIGN KEY(owner_id,skill_id) REFERENCES skills(owner_id,id),
 FOREIGN KEY(owner_id,goal_id,skill_id) REFERENCES learning_goals(owner_id,id,skill_id),
 FOREIGN KEY(owner_id,node_id) REFERENCES knowledge_nodes(owner_id,id),
 FOREIGN KEY(owner_id,enrollment_id,path_id) REFERENCES path_enrollments(owner_id,id,path_id),
 FOREIGN KEY(owner_id,entry_id,path_id) REFERENCES learning_path_nodes(owner_id,id,path_id),
 CHECK ((enrollment_id IS NULL AND entry_id IS NULL AND path_id IS NULL) OR (enrollment_id IS NOT NULL AND entry_id IS NOT NULL AND path_id IS NOT NULL))
);
CREATE INDEX tasks_owner_idx ON learning_tasks(owner_id,updated_at DESC,id);
CREATE TABLE learning_sessions (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, task_id UUID NOT NULL,
 state VARCHAR(16) NOT NULL CHECK(state IN ('RUNNING','PAUSED','ENDED')),
 started_at TIMESTAMPTZ NOT NULL, segment_started_at TIMESTAMPTZ, ended_at TIMESTAMPTZ,
 accumulated_millis BIGINT NOT NULL DEFAULT 0 CHECK(accumulated_millis>=0), revision INT NOT NULL DEFAULT 0 CHECK(revision>=0),
 UNIQUE(owner_id,id), FOREIGN KEY(owner_id,task_id) REFERENCES learning_tasks(owner_id,id),
 CHECK((state='RUNNING')=(segment_started_at IS NOT NULL)), CHECK((state='ENDED')=(ended_at IS NOT NULL)), CHECK(ended_at IS NULL OR ended_at>=started_at)
);
CREATE UNIQUE INDEX session_open_owner_idx ON learning_sessions(owner_id) WHERE state<>'ENDED';
CREATE INDEX sessions_owner_idx ON learning_sessions(owner_id,started_at DESC,id);
CREATE TABLE learning_session_commands (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, request_key VARCHAR(100) NOT NULL, session_id UUID NOT NULL,
 action VARCHAR(16) NOT NULL CHECK(action IN ('START','PAUSE','RESUME','FINISH')), request_revision INT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(owner_id,request_key),
 FOREIGN KEY(owner_id,session_id) REFERENCES learning_sessions(owner_id,id)
);
CREATE TABLE learning_notes (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, task_id UUID NOT NULL, body TEXT NOT NULL CHECK(length(body)<=20000),
 revision INT NOT NULL DEFAULT 0 CHECK(revision>=0), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(owner_id,task_id), FOREIGN KEY(owner_id,task_id) REFERENCES learning_tasks(owner_id,id)
);
CREATE TABLE learning_resources (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES users(id), title VARCHAR(160) NOT NULL CHECK(length(btrim(title))>0),
 resource_type VARCHAR(16) NOT NULL CHECK(resource_type IN ('WEB','VIDEO','BOOK','OTHER')),
 url VARCHAR(2048) NOT NULL, status VARCHAR(16) NOT NULL CHECK(status IN ('ACTIVE','ARCHIVED')),
 revision INT NOT NULL DEFAULT 0 CHECK(revision>=0), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(owner_id,id)
);
CREATE TABLE node_resources (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, node_id UUID NOT NULL, resource_id UUID NOT NULL,
 UNIQUE(owner_id,node_id,resource_id), FOREIGN KEY(owner_id,node_id) REFERENCES knowledge_nodes(owner_id,id),
 FOREIGN KEY(owner_id,resource_id) REFERENCES learning_resources(owner_id,id)
);
CREATE FUNCTION reject_learning_history_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Learning path versions and session commands are immutable'; END;
$$;
CREATE TRIGGER paths_immutable BEFORE UPDATE OR DELETE ON learning_paths FOR EACH ROW EXECUTE FUNCTION reject_learning_history_change();
CREATE TRIGGER stages_immutable BEFORE UPDATE OR DELETE ON learning_path_stages FOR EACH ROW EXECUTE FUNCTION reject_learning_history_change();
CREATE TRIGGER path_nodes_immutable BEFORE UPDATE OR DELETE ON learning_path_nodes FOR EACH ROW EXECUTE FUNCTION reject_learning_history_change();
CREATE TRIGGER session_commands_immutable BEFORE UPDATE OR DELETE ON learning_session_commands FOR EACH ROW EXECUTE FUNCTION reject_learning_history_change();
