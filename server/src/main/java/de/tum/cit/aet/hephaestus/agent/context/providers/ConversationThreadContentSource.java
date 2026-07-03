package de.tum.cit.aet.hephaestus.agent.context.providers;

import static de.tum.cit.aet.hephaestus.agent.handler.spi.JobMetadataReader.requireText;

import de.tum.cit.aet.hephaestus.agent.context.ContentSource;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.providers.mentor.SlackConversationProjector;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Materialises the CONVERSATION_THREAD detection context under {@code inputs/context/} — the repo-less,
 * no-diff counterpart of {@link IssueContentSource} for a settled Slack thread:
 *
 * <ul>
 *   <li>{@code conversation_thread.json} — the ordered, non-tombstoned human turns of one thread, each with its
 *       author and text, wrapped in the untrusted-content quarantine envelope.</li>
 * </ul>
 *
 * <p>Reuses the {@link SlackConversationProjector}, which reads the Slack substrate via raw
 * {@code JdbcTemplate} with an explicit {@code workspace_id} predicate (the tenancy inspector is bypassed for
 * raw JDBC). Required: a job whose metadata does not name a thread is a preparation failure.
 */
@Component
public class ConversationThreadContentSource implements ContentSource {

    private static final Logger log = LoggerFactory.getLogger(ConversationThreadContentSource.class);

    /** The single context file this provider emits — the only file a repo-less conversation job carries. */
    static final String OUTPUT_KEY = OUTPUT_PREFIX + "conversation_thread.json";

    private final ObjectMapper objectMapper;
    private final SlackConversationProjector projector;

    public ConversationThreadContentSource(ObjectMapper objectMapper, SlackConversationProjector projector) {
        this.objectMapper = objectMapper;
        this.projector = projector;
    }

    @Override
    public String originId() {
        return "slack";
    }

    @Override
    public boolean supports(ContextRequest request) {
        return request instanceof ContextRequest.ConversationReviewRequest;
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(ContextRequest request, Map<String, byte[]> files) {
        AgentJob job = ((ContextRequest.ConversationReviewRequest) request).job();
        JsonNode metadata = job.getMetadata();
        if (metadata == null || metadata.isNull() || metadata.isMissingNode()) {
            throw new JobPreparationException("Job has no metadata: jobId=" + job.getId());
        }
        if (job.getWorkspace() == null) {
            throw new JobPreparationException("Job has no workspace: jobId=" + job.getId());
        }
        long workspaceId = job.getWorkspace().getId();
        String channelId = requireText(metadata, "slack_channel_id");
        String threadTs = requireText(metadata, "slack_thread_ts");

        ObjectNode payload = projector.buildThreadPayload(workspaceId, channelId, threadTs);
        try {
            files.put(OUTPUT_KEY, objectMapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new JobPreparationException("Failed to serialize conversation_thread.json: " + e.getMessage(), e);
        }
        log.info(
            "Conversation context built: channel={}, threadTs={}, turns={}, jobId={}",
            channelId,
            threadTs,
            payload.path("messageCount").asInt(),
            job.getId()
        );
    }
}
