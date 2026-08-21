-- Applied once to a freshly restored clone, before this preview's application server starts.
--
-- The clone is another instance's live database, so two things have to be made true before it boots.
--
--   1. SILENCE — a clone must not act. It holds real workspaces, real connections and real
--      credentials, so everything that would post, deliver or schedule work still points at the
--      originals. Triggers, schedules and in-flight jobs are stopped. Nothing is deleted: the data
--      and the settings stay visible, and a tester can re-enable one workspace deliberately.
--
--   2. RE-HOME — a clone must not keep the source instance's identity. A preview reads the clone
--      with the source instance's encryption key, because that is the only key those rows can be
--      read with. The same key also unseals the source's JWT signing key and decrypts its OAuth
--      client secrets. Both belong to the source deployment, not this one, and are replaced here.
--
-- seed-loader runs this with ON_ERROR_STOP=1 and does not write the seed marker unless it succeeds,
-- so a preview is never left half-sanitized: the next deployment retries the whole clone.

-- ─── 1. Silence ──────────────────────────────────────────────────────────────────────────────────

UPDATE workspace
   SET practice_review_auto_trigger_enabled = FALSE,
       practice_review_manual_trigger_enabled = FALSE;

UPDATE workspace_agent_binding
   SET enabled = FALSE,
       updated_at = NOW()
 WHERE purpose = 'PRACTICE_REVIEW';

UPDATE review_sweep_schedule
   SET enabled = FALSE,
       updated_at = NOW();

UPDATE agent_job
   SET status = 'CANCELLED',
       completed_at = COALESCE(completed_at, NOW()),
       worker_id = NULL,
       error_message = 'Cancelled when staging data was cloned into a preview.',
       cancellation_reason = NULL
 WHERE status IN ('QUEUED', 'RUNNING');

UPDATE agent_job
   SET delivery_status = 'FAILED',
       error_message = CONCAT_WS(E'\n', NULLIF(error_message, ''),
           'Delivery paused when staging data was cloned into a preview.')
 WHERE status = 'COMPLETED'
   AND delivery_status = 'PENDING';

UPDATE sync_job
   SET status = 'CANCELLED',
       cancel_requested = TRUE,
       finished_at = COALESCE(finished_at, NOW()),
       heartbeat_at = NULL,
       error_summary = 'Cancelled when staging data was cloned into a preview.'
 WHERE status IN ('PENDING', 'RUNNING');

-- ─── 2. Re-home ──────────────────────────────────────────────────────────────────────────────────

-- Cloned sessions were issued by the source instance to its users. A preview has no use for them,
-- and they are the only thing that could carry a signed-in session across the clone.
DELETE FROM issued_jwt;

-- Left in place, this preview would mint its own tokens signed with the source instance's
-- production signing key. Emptied, JwtSigningKeyService bootstraps a sealed signing identity that
-- belongs to this preview alone.
DELETE FROM jwt_signing_key;

-- The cloned rows carry the source instance's OAuth apps, whose callback URLs are registered
-- against the source's hostname — a provider rejects every sign-in that starts from a preview host.
-- LoginProviderService seeds this table from this deployment's own environment when a registration
-- id is absent, so emptying it hands the preview its own login apps. Accounts are unaffected:
-- identity_link keys on identity_provider, not on this table.
DELETE FROM login_provider;
