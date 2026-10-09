-- Heph chat history and conversation copies as v0.94.2 keeps them, for the release upgrade to the Heph ledger.
-- Run once on the previous release's database after its workspace exists; scripts/release-upgrade-test.ts.
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
 INSERT INTO chat_thread(id,workspace_id,account_id,surface,created_at)
 VALUES('00000000-0000-0000-0000-000000990051',ws,developer,'WEB',now());
 INSERT INTO chat_message(id,workspace_id,thread_id,role,status,parts,created_at) VALUES
  ('00000000-0000-0000-0000-000000990052',ws,'00000000-0000-0000-0000-000000990051','USER','completed',
   '[{"type":"text","text":"How are my reviews?"}]'::jsonb,now()),
  ('00000000-0000-0000-0000-000000990053',ws,'00000000-0000-0000-0000-000000990051','ASSISTANT','completed',
   '[{"type":"text","text":"Name why in the description."}]'::jsonb,now());
 INSERT INTO feedback(id,workspace_id,review_id,item_id,developer_id,destination,state,stop_reason,body,created_at,delivered_at)
 VALUES
  ('00000000-0000-0000-0000-000000990041',ws,'00000000-0000-0000-0000-000000990011',item_id,developer,
   'CONVERSATION','DELIVERED',NULL,'Explain the decision.',now(),now()),
  ('00000000-0000-0000-0000-000000990042',ws,'00000000-0000-0000-0000-000000990011',item_id,developer,
   'CONVERSATION','UNCONFIRMED','DELIVERY_UNCONFIRMED','Name the risk.',now(),NULL);
 INSERT INTO feedback_copy(id,workspace_id,feedback_id,kind,position,state,write_key,write_started_at,posted_at,
   chat_message_id,body,stop_reason,readback)
 VALUES
  ('00000000-0000-0000-0000-000000990043',ws,'00000000-0000-0000-0000-000000990041','CHAT_TURN',0,'POSTED',
   'heph-upgrade-posted',now(),now(),'00000000-0000-0000-0000-000000990053','Name why in the description.',NULL,NULL),
  ('00000000-0000-0000-0000-000000990044',ws,'00000000-0000-0000-0000-000000990042','CHAT_TURN',0,'WRITE_STARTED',
   'heph-upgrade-fenced',now(),NULL,'00000000-0000-0000-0000-000000990053',NULL,'DELIVERY_UNCONFIRMED','UNKNOWN');
END $$;
