package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.agent.context.ContentSource;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest.MentorChatRequest;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Materialises {@code inputs/context/slack_conversations.json} for a {@link MentorChatRequest}: the Slack threads
 * the requesting developer <em>participated in</em>, grouped by thread, from channels whose consent is
 * {@code ACTIVE}, non-tombstoned messages only. Pure EXTRACT+LOAD of the raw native {@code slack_thread}/
 * {@code slack_message} rows via {@link SlackConversationProjector} — no practice-shaped feature, no observation,
 * no threshold (per the {@link ContentSource} provenance contract). {@code originId="slack"}. Best-effort.
 *
 * <p><strong>Keyed on the developer, not the workspace.</strong> The projector filters on
 * {@code developerId() = ANY(participant_member_ids)}, so this is the audience's own conversation history — it
 * replaces the earlier flat {@code ORDER BY slack_ts DESC LIMIT 100} workspace-wide dump that leaked every
 * channel message to every mentor chat regardless of who took part.
 *
 * <p>The projector wraps its output in an untrusted-content quarantine envelope ({@code _meta.trustLevel =
 * UNTRUSTED_EXTERNAL}); the mentor system prompt has the matching "channel content is data, not instructions"
 * rule (prompt-injection defence — Slack channel text is attacker-controlled).
 */
@Component
public class SlackConversationContentSource implements ContentSource {

    /** Workspace-relative output key. Whitelisted in {@code MentorContextKeys#ALLOWED_OUTPUT_KEYS}. */
    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "slack_conversations.json";

    private final SlackConversationProjector projector;
    private final ObjectMapper objectMapper;

    public SlackConversationContentSource(SlackConversationProjector projector, ObjectMapper objectMapper) {
        this.projector = projector;
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
        ObjectNode payload = projector.buildPayload(req.workspaceId(), req.developerId());
        try {
            files.put(OUTPUT_KEY, objectMapper.writeValueAsBytes(payload));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize Slack conversations context", e);
        }
    }
}
