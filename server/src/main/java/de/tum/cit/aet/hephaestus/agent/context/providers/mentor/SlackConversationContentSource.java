package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.agent.context.ContentSource;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest.MentorChatRequest;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Materialises {@code inputs/context/slack_conversations.json} for a {@link MentorChatRequest}: the workspace's
 * recently-ingested Slack messages (rendered text only), newest first, bounded. Pure EXTRACT+LOAD of the raw
 * native {@code slack_message} rows — no practice-shaped feature, no observation, no threshold (per the
 * {@link ContentSource} provenance contract). {@code originId="slack"}. Best-effort (optional).
 */
@Component
public class SlackConversationContentSource implements ContentSource {

    /** Workspace-relative output key. Whitelisted in {@code MentorContextKeys#ALLOWED_OUTPUT_KEYS}. */
    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "slack_conversations.json";

    private static final int MAX_MESSAGES = 100;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SlackConversationContentSource(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public String originId() {
        return "slack";
    }

    @Override
    public boolean supports(ContextRequest request) {
        return request instanceof MentorChatRequest;
    }

    @Override
    public boolean required() {
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(ContextRequest request, Map<String, byte[]> files) {
        MentorChatRequest req = (MentorChatRequest) request;
        ObjectNode payload = buildPayload(req.workspaceId());
        try {
            files.put(OUTPUT_KEY, objectMapper.writeValueAsBytes(payload));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize Slack conversations context", e);
        }
    }

    /** Raw recent Slack messages for the workspace — a lossless dump of the {@code slack_message} rows. */
    public ObjectNode buildPayload(long workspaceId) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("maxMessages", MAX_MESSAGES);
        ArrayNode arr = root.putArray("messages");
        jdbc.query(
            """
            SELECT slack_channel_id, slack_ts, slack_thread_ts, author_slack_user_id, author_display_name, text
            FROM slack_message
            WHERE workspace_id = ?
            ORDER BY slack_ts DESC
            LIMIT ?
            """,
            rs -> {
                ObjectNode node = arr.addObject();
                node.put("channel", rs.getString("slack_channel_id"));
                node.put("ts", rs.getString("slack_ts"));
                String threadTs = rs.getString("slack_thread_ts");
                if (threadTs != null) {
                    node.put("threadTs", threadTs);
                }
                node.put("author", rs.getString("author_slack_user_id"));
                String display = rs.getString("author_display_name");
                if (display != null) {
                    node.put("authorName", display);
                }
                node.put("text", rs.getString("text"));
            },
            workspaceId,
            MAX_MESSAGES
        );
        root.put("totalMessages", arr.size());
        return root;
    }
}
