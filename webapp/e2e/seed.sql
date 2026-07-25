-- Seed data for the Playwright E2E (see e2e/README.md). Idempotent.
-- Creates the `e2e` workspace, an instance LLM connection with two curated models, one agent binding
-- per purpose (practice detection + mentor), and a signed-in account (id 1, created by the dev-login)
-- that is an ADMIN member so the SPA can navigate to the workspace. Run against the E2E Postgres
-- after the backend has applied its schema.

INSERT INTO workspace (id, account_login, account_type, created_at, display_name, is_publicly_viewable, slug, status,
  practices_enabled, achievements_enabled, leaderboard_enabled, progression_enabled, leagues_enabled,
  practice_review_auto_trigger_enabled, practice_review_manual_trigger_enabled, mentor_enabled)
VALUES (1, 'hephaestustest', 'ORG', now(), 'E2E Practice Detection', false, 'e2e', 'ACTIVE',
  true, false, false, false, false, true, true, true)
ON CONFLICT (id) DO NOTHING;

-- Instance LLM catalog: one connection (endpoint + protocol; the API key never leaves the proxy) and
-- the two models the workspace binds. PUBLIC visibility so no per-workspace grant row is needed.
INSERT INTO llm_connection (id, slug, display_name, base_url, api_protocol, auth_mode, enabled, created_at)
VALUES (1, 'e2e-gateway', 'E2E LLM Gateway', 'https://llm-gateway.example/api', 'openai-completions', 'BEARER', true, now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO llm_model (id, connection_id, slug, display_name, upstream_model_id, context_window, max_output_tokens, supports_reasoning, visibility, enabled, created_at)
VALUES (1, 1, 'claude-sonnet-4-5', 'Claude Sonnet 4.5', 'claude-sonnet-4-5', 200000, 64000, false, 'PUBLIC', true, now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO llm_model (id, connection_id, slug, display_name, upstream_model_id, context_window, max_output_tokens, supports_reasoning, visibility, enabled, created_at)
VALUES (2, 1, 'gpt-oss-120b', 'GPT-OSS 120B', 'openai/gpt-oss-120b', 128000, 32000, true, 'PUBLIC', true, now())
ON CONFLICT (id) DO NOTHING;

-- One binding per purpose: "what model runs detection" and "what model runs the mentor". At most one
-- row per (workspace, purpose), and exactly one of the two model columns is set.
INSERT INTO workspace_agent_binding (id, workspace_id, purpose, instance_model_id, enabled, timeout_seconds, max_concurrent_jobs, allow_internet, created_at, updated_at)
VALUES (1, 1, 'PRACTICE_DETECTION', 1, true, 600, 2, false, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO workspace_agent_binding (id, workspace_id, purpose, instance_model_id, enabled, timeout_seconds, max_concurrent_jobs, allow_internet, created_at, updated_at)
VALUES (2, 1, 'MENTOR', 2, true, 1200, 1, true, now(), now())
ON CONFLICT (id) DO NOTHING;

-- SCM identity + membership for the dev account (id 1). The SPA lists/navigates workspaces by
-- membership, so a dev admin needs to be a member even though the API would elevate it.
INSERT INTO git_provider (id, type, server_url, created_at) VALUES (1, 'GITLAB', 'https://gitlab.lrz.de', now())
ON CONFLICT (id) DO NOTHING;
INSERT INTO "user" (id, native_id, provider_id, login, type, avatar_url, html_url, created_at, updated_at)
VALUES (900001, 900001, 1, 'e2e', 'USER', '', 'https://gitlab.lrz.de/e2e', now(), now())
ON CONFLICT (id) DO NOTHING;
-- Resolve the dev account by its synthetic email (set by dev-login) rather than assuming id 1.
INSERT INTO identity_link (id, account_id, git_provider_id, subject, linked_at, linked_via, external_actor_id, username_at_signup)
SELECT 1, a.id, 1, '900001', now(), 'OAUTH_LOGIN', 900001, 'e2e'
FROM account a WHERE a.primary_email = 'e2e@dev.invalid'
ON CONFLICT (id) DO NOTHING;
INSERT INTO workspace_membership (workspace_id, user_id, role, league_points, hidden, created_at)
VALUES (1, 900001, 'ADMIN', 0, false, now())
ON CONFLICT DO NOTHING;

SELECT setval(pg_get_serial_sequence('llm_connection', 'id'), 10, true);
SELECT setval(pg_get_serial_sequence('llm_model', 'id'), 10, true);
SELECT setval(pg_get_serial_sequence('workspace_agent_binding', 'id'), 10, true);
SELECT setval(pg_get_serial_sequence('workspace', 'id'), 10, true);
