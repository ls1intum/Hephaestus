-- After the upgrade to the Heph ledger (ADR 0057 § Exception): the chat history, its votes and Slack thread links are
-- gone, and nothing converts them. The conversation feedback and its copies stay with their words and states, without
-- the message they were bound to. The new copy guard still refuses a second delivery of an old copy and admits the
-- first claim of a ready one. Pairs with released-seed.sql.
DO $$
DECLARE
 ws bigint;
 developer bigint;
BEGIN
 IF to_regclass('chat_thread') IS NOT NULL OR to_regclass('chat_message') IS NOT NULL
    OR to_regclass('chat_message_vote') IS NOT NULL OR to_regclass('mentor_slack_thread') IS NOT NULL THEN
  RAISE EXCEPTION 'the Heph chat tables survived the upgrade';
 END IF;
 IF to_regclass('mentor_conversation') IS NULL OR to_regclass('mentor_entry') IS NULL OR to_regclass('mentor_turn') IS NULL THEN
  RAISE EXCEPTION 'the Heph ledger is missing';
 END IF;
 SELECT f.workspace_id, f.developer_id INTO ws, developer FROM feedback f
  WHERE f.id = '00000000-0000-0000-0000-000000990045';
 IF ws IS NULL THEN RAISE EXCEPTION 'the ready conversation feedback did not survive the upgrade'; END IF;
 IF EXISTS(SELECT 1 FROM mentor_conversation WHERE workspace_id = ws)
    OR EXISTS(SELECT 1 FROM mentor_entry WHERE workspace_id = ws)
    OR EXISTS(SELECT 1 FROM mentor_turn WHERE workspace_id = ws) THEN
  RAISE EXCEPTION 'the upgrade converted earlier Heph chat history into the ledger';
 END IF;

 -- Feedback keeps its state, words, delivery time and stop reason.
 IF (SELECT count(*) FROM feedback f JOIN (VALUES
       ('00000000-0000-0000-0000-000000990041'::uuid, 'DELIVERED', 'Explain the decision.', NULL::text,
        '2026-01-02 03:05:06+00'::timestamptz),
       ('00000000-0000-0000-0000-000000990042'::uuid, 'UNCONFIRMED', 'Name the risk.', 'DELIVERY_UNCONFIRMED', NULL),
       ('00000000-0000-0000-0000-000000990045'::uuid, 'READY', 'Link the issue.', NULL, NULL)
     ) AS expected(id, state, body, stop_reason, delivered_at)
     ON f.id = expected.id AND f.state = expected.state AND f.body IS NOT DISTINCT FROM expected.body
       AND f.stop_reason IS NOT DISTINCT FROM expected.stop_reason
       AND f.delivered_at IS NOT DISTINCT FROM expected.delivered_at) <> 3 THEN
  RAISE EXCEPTION 'conversation feedback changed during the upgrade';
 END IF;

 -- Each copy keeps its state, words, fence and receipt; only the message link is gone. No copy is added.
 IF (SELECT count(*) FROM feedback_copy WHERE feedback_id IN ('00000000-0000-0000-0000-000000990041',
       '00000000-0000-0000-0000-000000990042', '00000000-0000-0000-0000-000000990045')) <> 3 THEN
  RAISE EXCEPTION 'the upgrade added or removed a conversation copy';
 END IF;
 IF (SELECT count(*) FROM feedback_copy c JOIN (VALUES
       ('00000000-0000-0000-0000-000000990043'::uuid, 'POSTED', 'Name why in the description.', NULL::text, NULL::text,
        '2026-01-02 03:05:00+00'::timestamptz, '2026-01-02 03:05:06+00'::timestamptz),
       ('00000000-0000-0000-0000-000000990044'::uuid, 'WRITE_STARTED', NULL, 'DELIVERY_UNCONFIRMED', 'UNKNOWN',
        '2026-01-02 03:05:00+00'::timestamptz, NULL),
       ('00000000-0000-0000-0000-000000990046'::uuid, 'PLANNED', NULL, NULL, NULL, NULL, NULL)
     ) AS expected(id, state, body, stop_reason, readback, write_started_at, posted_at)
     ON c.id = expected.id AND c.state = expected.state AND c.body IS NOT DISTINCT FROM expected.body
       AND c.stop_reason IS NOT DISTINCT FROM expected.stop_reason
       AND c.readback IS NOT DISTINCT FROM expected.readback
       AND c.write_started_at IS NOT DISTINCT FROM expected.write_started_at
       AND c.posted_at IS NOT DISTINCT FROM expected.posted_at
     WHERE c.mentor_entry_id IS NULL AND c.mentor_turn_id IS NULL) <> 3 THEN
  RAISE EXCEPTION 'a conversation copy changed during the upgrade';
 END IF;

 -- The guard keeps the words of a delivered copy.
 BEGIN
  UPDATE feedback_copy SET body = 'Other words.' WHERE id = '00000000-0000-0000-0000-000000990043';
  RAISE EXCEPTION 'the guard let a delivered conversation copy change its words';
 EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
 END;
 -- A claim with no known outcome never gets a second claim, so its feedback is never shown twice.
 BEGIN
  UPDATE feedback_copy SET mentor_turn_id = '00000000-0000-0000-0000-000000990071'
   WHERE id = '00000000-0000-0000-0000-000000990044';
  RAISE EXCEPTION 'the guard let an unknown conversation claim bind a new Heph turn';
 EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
 END;
 -- Ready feedback can still be shown: a running Heph turn of its developer claims the copy as the server does.
 -- The block always ends with the sentinel HU001, so the claim and its turn are undone.
 BEGIN
  INSERT INTO mentor_conversation(id, workspace_id, account_id, created_at, last_activity_at)
  VALUES('00000000-0000-0000-0000-000000990072', ws, developer, now(), now());
  INSERT INTO mentor_turn(id, workspace_id, conversation_id, request_id, status, surface, created_at)
  VALUES('00000000-0000-0000-0000-000000990071', ws, '00000000-0000-0000-0000-000000990072',
    'heph-upgrade-claim', 'RUNNING', 'APP', now());
  UPDATE feedback_copy SET state = 'WRITE_STARTED', write_started_at = now(),
    mentor_turn_id = '00000000-0000-0000-0000-000000990071', stop_reason = 'DELIVERY_UNCONFIRMED',
    last_error = NULL, readback = 'UNKNOWN'
   WHERE id = '00000000-0000-0000-0000-000000990046' AND kind = 'CHAT_TURN' AND state = 'PLANNED'
     AND write_started_at IS NULL AND posted_at IS NULL AND external_ref IS NULL
     AND mentor_turn_id IS NULL AND mentor_entry_id IS NULL AND body IS NULL AND write_key IS NOT NULL;
  IF NOT FOUND THEN RAISE EXCEPTION 'the ready conversation copy is no longer claimable'; END IF;
  RAISE EXCEPTION 'undo the claim' USING ERRCODE = 'HU001';
 EXCEPTION WHEN SQLSTATE 'HU001' THEN NULL;
 END;
 IF (SELECT state FROM feedback_copy WHERE id = '00000000-0000-0000-0000-000000990046') IS DISTINCT FROM 'PLANNED'
    OR EXISTS(SELECT 1 FROM mentor_turn WHERE id = '00000000-0000-0000-0000-000000990071') THEN
  RAISE EXCEPTION 'the claim check left data behind';
 END IF;
END $$;
