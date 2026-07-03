/**
 * Agent-owned SPI for the conversation-review read path — the dependency-inversion seam that lets the agent
 * mentor/detection pipeline consume settled Slack conversation threads <em>without</em> reaching into the
 * {@code integration.slack} schema.
 *
 * <p><strong>Why this exists.</strong> The mentor content sources, the derived-feedback consent gate, and the
 * conversation-detection scheduler all need three things off the Slack channel-ingest substrate: the projected
 * thread payload, the "is this thread's channel still ACTIVE" liveness set, and the settled-thread candidate
 * scan. Historically the agent read those directly with a raw {@code JdbcTemplate}
 * against {@code slack_thread}/{@code slack_message}/{@code slack_monitored_channel} — a hidden
 * {@code agent → integration.slack} coupling to another module's <em>physical schema</em> that the Modulith
 * import-check could not see (it inspects Java imports, not SQL strings).
 *
 * <p><strong>The inversion.</strong> These interfaces are owned by {@code agent} (the consumer) and
 * <em>implemented</em> by {@code integration.slack} (the table owner). Every edge therefore runs
 * {@code integration.slack → agent} — the same direction as the existing {@code mentor-chat} edge and the
 * {@code practices::spi} {@code ConversationFeedbackErasure} erasure inversion — so no bounded-context cycle
 * forms and a column rename in Slack becomes a compile error <em>inside Slack</em> rather than a silent runtime
 * break in the agent. Exposed to {@code integration.slack} via the {@code conversation-source} named interface.
 */
@org.springframework.modulith.NamedInterface("conversation-source")
package de.tum.cit.aet.hephaestus.agent.conversation;
