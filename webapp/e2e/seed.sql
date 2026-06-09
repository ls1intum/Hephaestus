-- Seed data for the Playwright E2E (see e2e/README.md). Idempotent.
-- Creates the `e2e` workspace + two agent runtimes, and a signed-in account (id 1, created by the
-- dev-login) that is an ADMIN member so the SPA can navigate to the workspace. Run against the E2E
-- Postgres after the backend has applied its schema.

INSERT INTO workspace (id, account_login, account_type, created_at, display_name, is_publicly_viewable, slug, status,
  practices_enabled, achievements_enabled, leaderboard_enabled, progression_enabled, leagues_enabled,
  practice_review_auto_trigger_enabled, practice_review_manual_trigger_enabled, mentor_enabled)
VALUES (1, 'hephaestustest', 'ORG', now(), 'E2E Practice Detection', false, 'e2e', 'ACTIVE',
  true, false, false, false, false, true, true, true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO agent_config (id, workspace_id, name, enabled, llm_provider, model_name, timeout_seconds, max_concurrent_jobs, allow_internet, created_at, credential_mode)
VALUES (1, 1, 'Default reviewer', true, 'ANTHROPIC', 'claude-sonnet-4-5', 600, 2, false, now(), 'PROXY')
ON CONFLICT (id) DO NOTHING;

INSERT INTO agent_config (id, workspace_id, name, enabled, llm_provider, model_name, llm_base_url, timeout_seconds, max_concurrent_jobs, allow_internet, created_at, credential_mode)
VALUES (2, 1, 'GPU ASE (gpt-oss-120b)', true, 'OPENAI', 'openai/gpt-oss-120b', 'https://gpu.ase.cit.tum.de/api', 1200, 1, true, now(), 'API_KEY')
ON CONFLICT (id) DO NOTHING;

-- SCM identity + membership for the dev account (id 1). The SPA lists/navigates workspaces by
-- membership, so a dev admin needs to be a member even though the API would elevate it.
INSERT INTO git_provider (id, type, server_url, created_at) VALUES (1, 'GITLAB', 'https://gitlab.lrz.de', now())
ON CONFLICT (id) DO NOTHING;
INSERT INTO "user" (id, native_id, provider_id, login, type, avatar_url, html_url, created_at, updated_at)
VALUES (900001, 900001, 1, 'e2e', 'USER', '', 'https://gitlab.lrz.de/e2e', now(), now())
ON CONFLICT (id) DO NOTHING;
INSERT INTO identity_link (id, account_id, git_provider_id, subject, linked_at, linked_via, external_actor_id, username_at_signup)
VALUES (1, 1, 1, '900001', now(), 'OAUTH_LOGIN', 900001, 'e2e')
ON CONFLICT (id) DO NOTHING;
INSERT INTO workspace_membership (workspace_id, user_id, role, league_points, hidden, created_at)
VALUES (1, 900001, 'ADMIN', 0, false, now())
ON CONFLICT DO NOTHING;

SELECT setval(pg_get_serial_sequence('agent_config', 'id'), 10, true);
SELECT setval(pg_get_serial_sequence('workspace', 'id'), 10, true);
