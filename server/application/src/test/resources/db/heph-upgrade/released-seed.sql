-- Heph chat history and conversation copies as the previous release keeps them, for the release upgrade to the Heph
-- ledger. Run once on the previous release's database after its workspace exists; scripts/release-upgrade-test.ts.
-- Delivery and claim timestamps are fixed for exact comparison. The unclaimed READY piece is created now
-- so normal conversation expiry does not turn migration preservation into an expiry test.
DO $$
DECLARE
 ws bigint;
 developer bigint;
 item_id bigint;
BEGIN
 SELECT w.id INTO ws FROM workspace w
  WHERE coalesce(to_jsonb(w)->>'workspace_slug', to_jsonb(w)->>'slug') = 'upgrade-fixture';
 SELECT m.account_id INTO developer FROM membership m WHERE m.workspace_id = ws ORDER BY m.account_id LIMIT 1;
 IF ws IS NULL OR developer IS NULL THEN RAISE EXCEPTION 'the upgrade fixture workspace and its member are missing'; END IF;
 INSERT INTO item(workspace_id,kind,source_id,created_at) VALUES(ws,'scm.pull_request',990001,now()) RETURNING id INTO item_id;
 INSERT INTO review(id,workspace_id,item_id,kind,item_version,origin,status,ordered_at,created_at)
 VALUES('00000000-0000-0000-0000-000000990011',ws,item_id,'ITEM','v1','REQUEST','RUNNING',now(),now());
 -- A web chat and a Slack direct-message chat, with a vote and the Slack thread link.
 INSERT INTO chat_thread(id,workspace_id,account_id,surface,created_at) VALUES
  ('00000000-0000-0000-0000-000000990051',ws,developer,'WEB',now()),
  ('00000000-0000-0000-0000-000000990061',ws,developer,'SLACK_DM',now());
 INSERT INTO chat_message(id,workspace_id,thread_id,role,status,parts,created_at) VALUES
  ('00000000-0000-0000-0000-000000990052',ws,'00000000-0000-0000-0000-000000990051','USER','completed',
   '[{"type":"text","text":"How are my reviews?"}]'::jsonb,now()),
  ('00000000-0000-0000-0000-000000990053',ws,'00000000-0000-0000-0000-000000990051','ASSISTANT','completed',
   '[{"type":"text","text":"Name why in the description."}]'::jsonb,now()),
  ('00000000-0000-0000-0000-000000990062',ws,'00000000-0000-0000-0000-000000990061','USER','completed',
   '[{"type":"text","text":"What is next?"}]'::jsonb,now()),
  ('00000000-0000-0000-0000-000000990063',ws,'00000000-0000-0000-0000-000000990061','ASSISTANT','completed',
   '[{"type":"text","text":"Answer the open review."}]'::jsonb,now());
 INSERT INTO chat_message_vote(message_id,created_at,is_upvoted,updated_at)
 VALUES('00000000-0000-0000-0000-000000990053',now(),true,now());
 INSERT INTO mentor_slack_thread(id,workspace_id,chat_thread_id,slack_team_id,slack_channel_id,slack_thread_ts,slack_user_id)
 VALUES('00000000-0000-0000-0000-000000990064',ws,'00000000-0000-0000-0000-000000990061','T0UPGRADE','D0UPGRADE',
   '1700000000.000100','U0UPGRADE');
 -- Conversation feedback: delivered, unconfirmed after a claim that has no known outcome, and ready but not shown.
 INSERT INTO feedback(id,workspace_id,review_id,item_id,developer_id,destination,state,stop_reason,body,created_at,delivered_at)
 VALUES
  ('00000000-0000-0000-0000-000000990041',ws,'00000000-0000-0000-0000-000000990011',item_id,developer,
   'CONVERSATION','DELIVERED',NULL,'Explain the decision.','2026-01-02 03:04:05+00','2026-01-02 03:05:06+00'),
  ('00000000-0000-0000-0000-000000990042',ws,'00000000-0000-0000-0000-000000990011',item_id,developer,
   'CONVERSATION','UNCONFIRMED','DELIVERY_UNCONFIRMED','Name the risk.','2026-01-02 03:04:05+00',NULL),
  ('00000000-0000-0000-0000-000000990045',ws,'00000000-0000-0000-0000-000000990011',item_id,developer,
   'CONVERSATION','READY',NULL,'Link the issue.',now(),NULL);
 INSERT INTO feedback_copy(id,workspace_id,feedback_id,kind,position,state,write_key,write_started_at,posted_at,
   chat_message_id,body,stop_reason,readback)
 VALUES
  ('00000000-0000-0000-0000-000000990043',ws,'00000000-0000-0000-0000-000000990041','CHAT_TURN',0,'POSTED',
   'heph-upgrade-posted','2026-01-02 03:05:00+00','2026-01-02 03:05:06+00','00000000-0000-0000-0000-000000990053',
   'Name why in the description.',NULL,NULL),
  ('00000000-0000-0000-0000-000000990044',ws,'00000000-0000-0000-0000-000000990042','CHAT_TURN',0,'WRITE_STARTED',
   'heph-upgrade-fenced','2026-01-02 03:05:00+00',NULL,'00000000-0000-0000-0000-000000990053',NULL,
   'DELIVERY_UNCONFIRMED','UNKNOWN'),
  ('00000000-0000-0000-0000-000000990046',ws,'00000000-0000-0000-0000-000000990045','CHAT_TURN',0,'PLANNED',
   'heph-upgrade-planned',NULL,NULL,NULL,NULL,NULL,NULL);
END $$;
