package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmission;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelopeWriter;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Unit tests for the conversation-detection handler: pure submission logic plus the repo-less spike
 * assertions (no SCM source mount, empty volume mounts).
 */
class ConversationReviewHandlerTest extends BaseUnitTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Mock
    private WorkspaceContextBuilder workspaceContextBuilder;

    @Mock
    private PracticeCatalogInjector practiceCatalogInjector;

    @Mock
    private PracticeDetectionDeliveryService deliveryService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    private ConversationReviewHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ConversationReviewHandler(
            objectMapper,
            workspaceContextBuilder,
            new TaskEnvelopeWriter(objectMapper),
            practiceCatalogInjector,
            new PracticeDetectionResultParser(objectMapper),
            deliveryService,
            eventPublisher,
            transactionTemplate
        );
    }

    private ConversationReviewSubmissionRequest sampleRequest() {
        return new ConversationReviewSubmissionRequest(555L, "C0ABC", "1700000000.100000", 42L, "1700000900.500000");
    }

    @Nested
    class CreateSubmission {

        @Test
        void buildsConversationMetadata() {
            JobSubmission submission = handler.createSubmission(sampleRequest());
            JsonNode metadata = submission.metadata();

            assertThat(metadata.get("artifact_type").asString()).isEqualTo("CONVERSATION_THREAD");
            assertThat(metadata.get("slack_thread_id").asLong()).isEqualTo(555L);
            assertThat(metadata.get("slack_channel_id").asString()).isEqualTo("C0ABC");
            assertThat(metadata.get("slack_thread_ts").asString()).isEqualTo("1700000000.100000");
            assertThat(metadata.get("about_user_id").asLong()).isEqualTo(42L);
        }

        @Test
        void idempotencyKeyCooldownScopesOnThreadPlusSubjectNotFreshness() {
            // The key ends in a disposable freshness segment (lastTs). AgentJobService.extractCooldownKeyPrefix
            // strips ONLY that trailing segment, so cooldown keys on (channel, thread, subject) — a late reply
            // with a NEW lastTs shares the prefix and does not re-fire.
            JobSubmission submission = handler.createSubmission(sampleRequest());
            assertThat(submission.idempotencyKey()).isEqualTo(
                "conversation_review:C0ABC:1700000000.100000:42:1700000900.500000"
            );
        }

        @Test
        void rejectsWrongRequestType() {
            assertThatThrownBy(() -> handler.createSubmission(new WrongRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected ConversationReviewSubmissionRequest");
        }
    }

    private record WrongRequest() implements de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest {}

    /**
     * The spike: a conversation-review job is REPO-LESS. It carries no SCM source mount and no volume
     * mounts, so the orchestrator/runner run without a clone. These lock that contract.
     */
    @Nested
    class RepoLessSpike {

        private AgentJob conversationJob() {
            var job = new AgentJob();
            job.setId(java.util.UUID.randomUUID());
            var workspace = new Workspace();
            workspace.setId(1L);
            job.setWorkspace(workspace);
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("artifact_type", "CONVERSATION_THREAD");
            metadata.put("slack_channel_id", "C0ABC");
            metadata.put("slack_thread_ts", "1700000000.100000");
            metadata.put("about_user_id", 42L);
            job.setMetadata(metadata);
            return job;
        }

        @Test
        void volumeMountsAreEmpty() {
            // The load-bearing repo-less contract: a CONVERSATION_REVIEW job binds NO volume mounts (it inherits the
            // default JobTypeHandler.volumeMounts() == Map.of(), unlike the PR handler that mounts the clone). This
            // empty map is exactly what lets the orchestrator/runner run without a repository clone.
            assertThat(handler.volumeMounts(conversationJob())).isEmpty();
        }

        @Test
        void prepareInputFilesWritesNoScmSourceAndOnlyContextPlusTask() {
            AgentJob job = conversationJob();
            // The only context provider that fires materialises conversation_thread.json — stub the builder.
            // The practice-catalog injection (inputs/practices/*) is a mocked no-op here; it writes no SCM source.
            when(workspaceContextBuilder.build(any())).thenReturn(
                Map.of(SandboxLayout.CONTEXT_PREFIX + "conversation_thread.json", "{\"messages\":[]}".getBytes())
            );

            Map<String, byte[]> files = handler.prepareInputFiles(job);

            // The conversation context file is the sole case input, alongside the task envelope — the
            // "context + task, nothing else" shape the test name asserts.
            assertThat(files).containsKey(SandboxLayout.CONTEXT_PREFIX + "conversation_thread.json");
            assertThat(files).containsKey(SandboxLayout.TASK_ENVELOPE_FILENAME);
            // Repo-less proof: no SCM source keep file is written anywhere in the prepared workspace.
            assertThat(files).doesNotContainKey(SandboxLayout.SCM_SOURCE_KEEP);
            assertThat(files.keySet()).noneMatch(k -> k.startsWith(SandboxLayout.SOURCES_PREFIX));
        }
    }
}
