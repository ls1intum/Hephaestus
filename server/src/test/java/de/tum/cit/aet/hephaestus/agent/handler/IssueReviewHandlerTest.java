package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmission;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelopeWriter;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Unit tests for the issue-detection handler's pure submission logic. */
class IssueReviewHandlerTest extends BaseUnitTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Mock
    private WorkspaceContextBuilder workspaceContextBuilder;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private PracticeDetectionDeliveryService deliveryService;

    @Mock
    private PullRequestCommentPoster commentPoster;

    private IssueReviewHandler handler;

    @BeforeEach
    void setUp() {
        handler = new IssueReviewHandler(
            objectMapper,
            workspaceContextBuilder,
            new TaskEnvelopeWriter(objectMapper),
            new PracticeCatalogInjector(objectMapper, practiceRepository),
            new PracticeDetectionResultParser(objectMapper),
            deliveryService,
            commentPoster
        );
    }

    private IssueReviewSubmissionRequest sampleRequest() {
        return new IssueReviewSubmissionRequest(
            777L,
            12,
            123L,
            "owner/repo",
            "Add dark mode",
            "Users want a dark theme toggle in settings.",
            "OPEN",
            java.time.Instant.ofEpochMilli(1_700_000_000_000L)
        );
    }

    @Nested
    class JobType {

        @Test
        void returnsIssueReview() {
            assertThat(handler.jobType()).isEqualTo(AgentJobType.ISSUE_REVIEW);
        }
    }

    @Nested
    class CreateSubmission {

        @Test
        void buildsIssueMetadata() {
            JobSubmission submission = handler.createSubmission(sampleRequest());
            JsonNode metadata = submission.metadata();

            assertThat(metadata.get("target_type").asString()).isEqualTo("ISSUE");
            assertThat(metadata.get("repository_id").asLong()).isEqualTo(123L);
            assertThat(metadata.get("repository_full_name").asString()).isEqualTo("owner/repo");
            assertThat(metadata.get("issue_id").asLong()).isEqualTo(777L);
            assertThat(metadata.get("issue_number").asInt()).isEqualTo(12);
            assertThat(metadata.get("title").asString()).isEqualTo("Add dark mode");
            assertThat(metadata.get("state").asString()).isEqualTo("OPEN");
        }

        @Test
        void idempotencyKeyHasDisposableFreshnessSegment() {
            // Key ends in a 4th (updatedAt) segment so extractCooldownKeyPrefix strips it and cooldown
            // scopes per-issue, NOT per-repo. An edited issue (new updatedAt) re-reviews.
            JobSubmission submission = handler.createSubmission(sampleRequest());
            assertThat(submission.idempotencyKey()).isEqualTo("issue_review:owner/repo:12:1700000000000");
        }

        @Test
        void rejectsWrongRequestType() {
            assertThatThrownBy(() -> handler.createSubmission(new WrongRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected IssueReviewSubmissionRequest");
        }
    }

    private record WrongRequest() implements de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest {}
}
