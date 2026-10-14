-- Apply to PostgreSQL after reviewing duplicate ACTIVE sprints/memberships.
-- The application also serializes sprint/task mutations using a project row lock.
-- Preflight: SELECT project_id, COUNT(*) FROM sprints WHERE sprint_status = 'ACTIVE'
-- GROUP BY project_id HAVING COUNT(*) > 1;
BEGIN;

ALTER TABLE projects ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
ALTER TABLE sprints ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
ALTER TABLE sprints ADD COLUMN IF NOT EXISTS analysis_revision bigint NOT NULL DEFAULT 0;
ALTER TABLE requirements ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
ALTER TABLE subtasks ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS uq_sprints_one_active_per_project
    ON sprints (project_id) WHERE sprint_status = 'ACTIVE';
CREATE UNIQUE INDEX IF NOT EXISTS uq_project_members
    ON project_members (project_id, user_id);

CREATE INDEX IF NOT EXISTS idx_sprints_project_end_date ON sprints (project_id, end_date DESC);
CREATE INDEX IF NOT EXISTS idx_requirements_project ON requirements (project_id);
CREATE INDEX IF NOT EXISTS idx_requirements_sprint ON requirements (sprint_id);
CREATE INDEX IF NOT EXISTS idx_subtasks_sprint_assignee ON subtasks (sprint_id, assigned_user_id);
CREATE INDEX IF NOT EXISTS idx_subtasks_requirement ON subtasks (requirement_id);
CREATE INDEX IF NOT EXISTS idx_status_history_task_changed ON task_status_history (task_id, changed_at);
CREATE INDEX IF NOT EXISTS idx_insights_sprint ON ai_insights (sprint_id);
CREATE INDEX IF NOT EXISTS idx_decision_notes_task ON decision_note (task_id);

COMMIT;
