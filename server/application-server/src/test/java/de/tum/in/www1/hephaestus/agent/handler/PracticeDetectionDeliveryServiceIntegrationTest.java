package de.tum.in.www1.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.config.AgentConfig;
import de.tum.in.www1.hephaestus.agent.config.AgentConfigRepository;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.agent.job.AgentJobRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.practices.finding.PracticeDetectionCompletedEvent;
import de.tum.in.www1.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.in.www1.hephaestus.practices.model.Practice;
import de.tum.in.www1.hephaestus.practices.model.PracticeFinding;
import de.tum.in.www1.hephaestus.practices.model.PracticeFindingTargetType;
import de.tum.in.www1.hephaestus.practices.model.Severity;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.TestUserFactory;
import de.tum.in.www1.hephaestus.testconfig.WorkspaceTestFactory;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Integration test for {@link PracticeDetectionDeliveryService} exercising real PostgreSQL
 * for finding persistence (INSERT ... ON CONFLICT DO NOTHING), negative cap enforcement,
 * verdict classification, and {@link PracticeDetectionCompletedEvent} publication.
 *
 * <p>No mocks required — this service layer does not call external APIs. It resolves practice
 * slugs against the DB and persists findings via {@code PracticeFindingRepository.insertIfAbsent()}.
 */
@DisplayName("PracticeDetectionDeliveryService Integration")
@RecordApplicationEvents
class PracticeDetectionDeliveryServiceIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private PracticeDetectionDeliveryService deliveryService;

    @Autowired
    private PracticeFindingRepository practiceFindingRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private AgentConfigRepository agentConfigRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    private Workspace workspace;
    private AgentJob agentJob;
    private User contributor;
    private Long prId;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();

        workspace = workspaceRepository.save(WorkspaceTestFactory.activeWorkspace("delivery-test"));

        createPractice("pr-description-quality", "PR Description Quality");
        createPractice("error-handling", "Error Handling");

        AgentConfig config = new AgentConfig();
        config.setWorkspace(workspace);
        config.setName("delivery-config");
        config.setEnabled(true);
        config.setAgentType(AgentType.CLAUDE_CODE);
        config.setLlmProvider(LlmProvider.ANTHROPIC);
        config.setCredentialMode(CredentialMode.PROXY);
        config.setTimeoutSeconds(300);
        config = agentConfigRepository.save(config);

        agentJob = new AgentJob();
        agentJob.setWorkspace(workspace);
        agentJob.setConfig(config);
        agentJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        agentJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        agentJob = agentJobRepository.save(agentJob);

        // Create contributor
        GitProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITHUB, "https://github.com")));
        contributor = TestUserFactory.createUser(200L, "test-pr-author", provider);
        contributor = userRepository.save(contributor);

        // Create repository
        Repository repo = new Repository();
        repo.setNativeId(1001L);
        repo.setProvider(provider);
        repo.setName("test-repo");
        repo.setNameWithOwner("org/test-repo");
        repo.setHtmlUrl("https://github.com/org/test-repo");
        repo.setDefaultBranch("main");
        repo = repositoryRepository.save(repo);

        // Create PR via upsertCore
        Instant now = Instant.now();
        pullRequestRepository.upsertCore(
            5001L,
            provider.getId(),
            42,
            "Test PR",
            "Test body",
            "OPEN",
            null,
            "https://github.com/org/test-repo/pull/42",
            false,
            null,
            0,
            now,
            now,
            now,
            contributor.getId(),
            repo.getId(),
            null,
            null,
            false,
            false,
            1,
            10,
            5,
            3,
            null,
            null,
            null,
            "feature/test",
            "main",
            "abc123",
            "def456",
            null
        );
        // Look up the PR ID
        prId = pullRequestRepository.findByRepositoryIdAndNumber(repo.getId(), 42).orElseThrow().getId();

        // Set metadata on job
        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        metadata.put("pull_request_id", prId);
        agentJob.setMetadata(metadata);
        agentJob = agentJobRepository.save(agentJob);
    }

    private Practice createPractice(String slug, String name) {
        Practice p = new Practice();
        p.setWorkspace(workspace);
        p.setSlug(slug);
        p.setName(name);
        p.setCategory("test");
        p.setDescription("Test " + slug);
        p.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
        return practiceRepository.save(p);
    }

    private ValidatedFinding finding(String slug, Verdict verdict) {
        return new ValidatedFinding(slug, "Test: " + slug, verdict, Severity.INFO, 0.9f, null, null, null, null);
    }

    @Nested
    @DisplayName("End-to-end delivery")
    class EndToEnd {

        @Test
        @DisplayName("valid findings create PracticeFinding rows in DB")
        void validFindingsPersistedToDb() {
            var findings = List.of(
                finding("pr-description-quality", Verdict.POSITIVE),
                finding("error-handling", Verdict.NEGATIVE)
            );

            var result = deliveryService.deliver(agentJob, findings);

            assertThat(result.inserted()).isEqualTo(2);
            assertThat(result.discardedUnknownSlug()).isZero();
            assertThat(result.hasNegative()).isTrue();

            List<PracticeFinding> persisted = practiceFindingRepository.findAll();
            assertThat(persisted).hasSize(2);
            assertThat(persisted)
                .extracting(PracticeFinding::getVerdict)
                .containsExactlyInAnyOrder(Verdict.POSITIVE, Verdict.NEGATIVE);
            assertThat(persisted)
                .extracting(PracticeFinding::getConfidence)
                .allMatch(c -> c == 0.9f);
        }

        @Test
        @DisplayName("re-delivering same job creates no duplicates")
        void idempotentRedelivery() {
            var findings = List.of(finding("pr-description-quality", Verdict.POSITIVE));

            var first = deliveryService.deliver(agentJob, findings);
            var second = deliveryService.deliver(agentJob, findings);

            assertThat(first.inserted()).isEqualTo(1);
            assertThat(second.inserted()).isZero();
            assertThat(second.discardedDuplicate()).isEqualTo(1);
            assertThat(practiceFindingRepository.findAll()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Practice resolution")
    class PracticeResolution {

        @Test
        @DisplayName("unknown slugs are skipped without failing")
        void unknownSlugsSkipped() {
            var findings = List.of(
                finding("pr-description-quality", Verdict.POSITIVE),
                finding("nonexistent-practice", Verdict.POSITIVE)
            );

            var result = deliveryService.deliver(agentJob, findings);

            assertThat(result.inserted()).isEqualTo(1);
            assertThat(result.discardedUnknownSlug()).isEqualTo(1);
            assertThat(practiceFindingRepository.findAll()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Negative cap enforcement")
    class NegativeCapEnforcement {

        @Test
        @DisplayName("caps negatives per practice at configured limit")
        void capsNegatives() {
            var findings = new ArrayList<ValidatedFinding>();
            for (int i = 0; i < 7; i++) {
                findings.add(
                    new ValidatedFinding(
                        "pr-description-quality",
                        "Negative finding " + i,
                        Verdict.NEGATIVE,
                        Severity.MINOR,
                        0.8f,
                        null,
                        null,
                        null,
                        null
                    )
                );
            }

            var result = deliveryService.deliver(agentJob, findings);

            // Default cap is 5
            assertThat(result.inserted()).isEqualTo(5);
            assertThat(result.discardedOverCap()).isEqualTo(2);
            assertThat(practiceFindingRepository.findAll()).hasSize(5);
        }
    }

    @Nested
    @DisplayName("Event publication")
    class EventPublication {

        @Test
        @DisplayName("publishes PracticeDetectionCompletedEvent after persistence")
        void publishesEvent() {
            var findings = List.of(finding("pr-description-quality", Verdict.POSITIVE));

            deliveryService.deliver(agentJob, findings);

            List<PracticeDetectionCompletedEvent> events = applicationEvents
                .stream(PracticeDetectionCompletedEvent.class)
                .toList();
            assertThat(events).hasSize(1);
            PracticeDetectionCompletedEvent event = events.get(0);
            assertThat(event.agentJobId()).isEqualTo(agentJob.getId());
            assertThat(event.workspaceId()).isEqualTo(workspace.getId());
            assertThat(event.targetType()).isEqualTo(PracticeFindingTargetType.PULL_REQUEST);
            assertThat(event.targetId()).isEqualTo(prId);
            assertThat(event.findingsInserted()).isEqualTo(1);
            assertThat(event.findingsDiscarded()).isZero();
            assertThat(event.hasNegative()).isFalse();
            assertThat(event.contributorId()).isEqualTo(contributor.getId());
        }

        @Test
        @DisplayName("empty findings list publishes event with zero counts")
        void emptyFindingsPublishesZeroEvent() {
            var result = deliveryService.deliver(agentJob, List.of());

            assertThat(result.inserted()).isZero();
            assertThat(result.hasNegative()).isFalse();

            List<PracticeDetectionCompletedEvent> events = applicationEvents
                .stream(PracticeDetectionCompletedEvent.class)
                .toList();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).findingsInserted()).isZero();
            assertThat(events.get(0).findingsDiscarded()).isZero();
            assertThat(events.get(0).hasNegative()).isFalse();
        }
    }

    @Nested
    @DisplayName("Non-negative verdicts")
    class NonNegativeVerdicts {

        @Test
        @DisplayName("POSITIVE verdicts persisted without triggering hasNegative")
        void positiveVerdictsDoNotTriggerHasNegative() {
            var findings = List.of(
                finding("pr-description-quality", Verdict.POSITIVE),
                finding("error-handling", Verdict.POSITIVE)
            );

            var result = deliveryService.deliver(agentJob, findings);

            assertThat(result.inserted()).isEqualTo(2);
            assertThat(result.hasNegative()).isFalse();

            List<PracticeFinding> persisted = practiceFindingRepository.findAll();
            assertThat(persisted).hasSize(2);
            assertThat(persisted)
                .extracting(PracticeFinding::getVerdict)
                .containsExactlyInAnyOrder(Verdict.POSITIVE, Verdict.POSITIVE);
        }
    }

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("throws when PR not found")
        void throwsWhenPrNotFound() {
            ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
            metadata.put("pull_request_id", 999999L);
            agentJob.setMetadata(metadata);
            agentJob = agentJobRepository.save(agentJob);

            var findings = List.of(finding("pr-description-quality", Verdict.POSITIVE));

            assertThatThrownBy(() -> deliveryService.deliver(agentJob, findings))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("Pull request not found");
        }
    }
}
