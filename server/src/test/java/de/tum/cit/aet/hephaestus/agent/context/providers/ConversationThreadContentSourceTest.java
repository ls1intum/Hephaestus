package de.tum.cit.aet.hephaestus.agent.context.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.providers.mentor.SlackConversationProjector;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Unit tests for the S11 conversation-thread content source. */
class ConversationThreadContentSourceTest extends BaseUnitTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Mock
    private SlackConversationProjector projector;

    private ConversationThreadContentSource source;

    @BeforeEach
    void setUp() {
        source = new ConversationThreadContentSource(objectMapper, projector);
    }

    private AgentJob conversationJob() {
        var job = new AgentJob();
        var workspace = new Workspace();
        workspace.setId(7L);
        job.setWorkspace(workspace);
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("slack_channel_id", "C0ABC");
        metadata.put("slack_thread_ts", "1700000000.100000");
        job.setMetadata(metadata);
        return job;
    }

    @Test
    void supportsOnlyConversationReviewRequests() {
        assertThat(source.supports(new ContextRequest.ConversationReviewRequest(conversationJob()))).isTrue();
        assertThat(source.supports(new ContextRequest.MentorChatRequest(1L, 2L, UUID.randomUUID()))).isFalse();
    }

    @Test
    void isRequiredSoAMissingThreadAbortsRatherThanHollowPositive() {
        // An empty thread is NOT a failure (buildThreadPayload returns empty messages); a genuine read failure
        // is, so the provider is required — mirrors IssueContentSource.
        assertThat(source.required()).isTrue();
    }

    @Test
    void writesConversationThreadJsonFromProjector() {
        AgentJob job = conversationJob();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("channel", "C0ABC");
        payload.put("messageCount", 3);
        when(projector.buildThreadPayload(7L, "C0ABC", "1700000000.100000")).thenReturn(payload);

        Map<String, byte[]> files = new HashMap<>();
        source.contribute(new ContextRequest.ConversationReviewRequest(job), files);

        assertThat(files).containsKey("inputs/context/conversation_thread.json");
        String written = new String(files.get("inputs/context/conversation_thread.json"));
        assertThat(written).contains("\"channel\":\"C0ABC\"").contains("\"messageCount\":3");
    }
}
