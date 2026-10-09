-- After the upgrade to the Heph ledger: the chat history is gone, and the conversation copies and their feedback
-- stay, without the message they were bound to (ADR 0057 § Schema). Pairs with released-seed.sql.
DO $$
BEGIN
 IF to_regclass('chat_thread') IS NOT NULL OR to_regclass('chat_message') IS NOT NULL
    OR to_regclass('chat_message_vote') IS NOT NULL OR to_regclass('mentor_slack_thread') IS NOT NULL THEN
  RAISE EXCEPTION 'the Heph chat tables survived the upgrade';
 END IF;
 IF to_regclass('mentor_conversation') IS NULL OR to_regclass('mentor_entry') IS NULL OR to_regclass('mentor_turn') IS NULL THEN
  RAISE EXCEPTION 'the Heph ledger is missing';
 END IF;
 IF (SELECT count(*) FROM feedback_copy c JOIN feedback f ON f.id = c.feedback_id
     WHERE (c.id, c.state, f.state) IN (
       ('00000000-0000-0000-0000-000000990043'::uuid, 'POSTED', 'DELIVERED'),
       ('00000000-0000-0000-0000-000000990044'::uuid, 'WRITE_STARTED', 'UNCONFIRMED'))
       AND c.write_started_at IS NOT NULL AND c.mentor_entry_id IS NULL AND c.mentor_turn_id IS NULL) <> 2 THEN
  RAISE EXCEPTION 'a conversation copy or its feedback changed during the upgrade';
 END IF;
 IF (SELECT body FROM feedback_copy WHERE id = '00000000-0000-0000-0000-000000990043') IS DISTINCT FROM
    'Name why in the description.' THEN
  RAISE EXCEPTION 'the words a delivered conversation copy recorded changed during the upgrade';
 END IF;
END $$;
