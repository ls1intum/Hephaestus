package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

class FeedbackPlacementRepositoryIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private FeedbackPlacementRepository placementRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private IdentityProviderRepository identityProviderRepository;

    private Workspace workspace;
    private AgentJob job;
    private User recipient;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        workspace = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("feedback-placement-test"));
        job = new AgentJob();
        job.setWorkspace(workspace);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        job = agentJobRepository.save(job);
        IdentityProvider provider = identityProviderRepository
            .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
            .orElseGet(() ->
                identityProviderRepository.save(new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com"))
            );
        recipient = userRepository.save(TestUserFactory.createUser(100L, "recipient", provider));
    }

    @Test
    void latestDeliveredSummaryIgnoresNewerInlineOnlyFeedback() {
        String threadKey = "thread-key";
        Feedback summary = saveFeedback(0, threadKey, Instant.parse("2026-01-01T00:00:00Z"));
        placementRepository.save(
            FeedbackPlacement.builder()
                .feedback(summary)
                .placementType(PlacementType.SUMMARY)
                .postedCommentRef("summary-1")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build()
        );
        Feedback inlineOnly = saveFeedback(1, threadKey, Instant.parse("2026-01-02T00:00:00Z"));
        placementRepository.save(
            FeedbackPlacement.builder()
                .feedback(inlineOnly)
                .placementType(PlacementType.INLINE)
                .anchorKind(PlacementAnchorKind.LINE)
                .anchorPath("src/Foo.java")
                .anchorStartLine(10)
                .anchorSide(PlacementAnchorSide.NEW)
                .postedCommentRef("inline-1")
                .createdAt(Instant.parse("2026-01-02T00:00:00Z"))
                .build()
        );

        assertThat(placementRepository.findLatestDeliveredSummary(threadKey))
            .get()
            .satisfies(placement -> {
                assertThat(placement.getFeedbackId()).isEqualTo(summary.getId());
                assertThat(placement.getPostedCommentRef()).isEqualTo("summary-1");
            });
    }

    private Feedback saveFeedback(int position, String threadKey, Instant createdAt) {
        return feedbackRepository.save(
            Feedback.builder()
                .agentJobId(job.getId())
                .workspaceId(workspace.getId())
                .artifactKind(ArtifactKinds.PULL_REQUEST)
                .artifactId(42L)
                .recipientUserId(recipient.getId())
                .aboutUserId(recipient.getId())
                .channel(FeedbackChannel.IN_CONTEXT)
                .position(position)
                .deliveryState(FeedbackDeliveryState.DELIVERED)
                .source(FeedbackSource.AGENT)
                .threadKey(threadKey)
                .createdAt(createdAt)
                .deliveredAt(createdAt)
                .build()
        );
    }
}
