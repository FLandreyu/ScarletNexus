-- Optional dedicated-role installations; ordinary local installs retain their existing role.
DO $$
BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='scarlet_runtime') THEN
  GRANT USAGE ON SCHEMA public TO scarlet_runtime;
  GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO scarlet_runtime;
  REVOKE ALL ON TABLE flyway_schema_history FROM scarlet_runtime;
  GRANT SELECT ON TABLE flyway_schema_history TO scarlet_runtime;
  REVOKE UPDATE,DELETE ON audit_events,knowledge_graph_versions,graph_publish_keys,
   learning_paths,learning_path_stages,learning_path_nodes,learning_session_commands,
   learning_attempts,attempt_reviews,learning_evidence,mastery_assessments,assessment_evidence,
   review_events,review_cycle_tasks,daily_plans,daily_plan_items,daily_plan_limits,
   task_adjustments,task_splits FROM scarlet_runtime;
 END IF;
END $$;
