package de.tum.in.www1.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.config.AgentConfig;
import de.tum.in.www1.hephaestus.agent.config.AgentConfigRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.practices.model.Practice;
import de.tum.in.www1.hephaestus.practices.spi.UserRoleChecker;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.TestUserFactory;
import de.tum.in.www1.hephaestus.testconfig.WorkspaceTestFactory;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration test for {@link PracticeReviewDetectionGate} exercising real PostgreSQL
 * queries for workspace resolution, agent config checking, and JSONB practice matching.
 *
 * <p>Primary integration value: workspace resolver heuristic, agent config existence check,
 * and JSONB containment query ({@code triggerEvents @> ?}) against real PostgreSQL.
 *
 * <p>Mocks only the external-facing SPI ({@link UserRoleChecker} for Keycloak).
 * Label/draft/assignee checks operate on the in-memory PR object passed to the gate
 * (matching production behavior where the PR is loaded once by the caller).
 */
@DisplayName("PracticeReviewDetectionGate integration")
class PracticeDetectionGateIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private PracticeReviewDetectionGate gate;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private AgentConfigRepository agentConfigRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LabelRepository labelRepository;

    @MockitoBean
    private UserRoleChecker userRoleChecker;

    private Workspace workspace;
    private Repository repo;
    private GitProvider provider;
    private User assignee;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();

        // Workspace with accountLogin = "org" so WorkspaceResolver heuristic matches "org/repo"
        workspace = WorkspaceTestFactory.activeWorkspace("gate-test");
        workspace.setAccountLogin("org");
        workspace = workspaceRepository.save(workspace);

        // Agent config (enabled)
        AgentConfig config = new AgentConfig();
        config.setWorkspace(workspace);
        config.setName("gate-config");
        config.setEnabled(true);
        config.setAgentType(AgentType.CLAUDE_CODE);
        config.setLlmProvider(LlmProvider.ANTHROPIC);
        config.setCredentialMode(CredentialMode.PROXY);
        config.setTimeoutSeconds(300);
        agentConfigRepository.save(config);

        provider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITHUB, "https://github.com")));

        assignee = TestUserFactory.createUser(400L, "assignee-user", provider);
        assignee = userRepository.save(assignee);

        repo = new Repository();
        repo.setNativeId(3001L);
        repo.setProvider(provider);
        repo.setName("gate-repo");
        repo.setNameWithOwner("org/gate-repo");
        repo.setHtmlUrl("https://github.com/org/gate-repo");
        repo.setDefaultBranch("main");
        repo = repositoryRepository.save(repo);

        // Mock role checker: healthy + assignee has role
        when(userRoleChecker.isHealthy()).thenReturn(true);
        when(userRoleChecker.hasRole(eq("assignee-user"), anyString())).thenReturn(true);
    }

    private Practice createPractice(String slug, String name, List<String> triggerEvents, boolean active) {
        Practice p = new Practice();
        p.setWorkspace(workspace);
        p.setSlug(slug);
        p.setName(name);
        p.setCategory("test");
        p.setDescription("Test " + slug);
        p.setTriggerEvents(OBJECT_MAPPER.valueToTree(triggerEvents));
        p.setActive(active);
        return practiceRepository.save(p);
    }

    private PullRequest createPullRequest(boolean isDraft, Set<Label> labels, Set<User> assignees) {
        Instant now = Instant.now();
        pullRequestRepository.upsertCore(
            7001L,
            provider.getId(),
            100,
            "Gate Test PR",
            "Body",
            "OPEN",
            null,
            "https://github.com/org/gate-repo/pull/100",
            false, // isLocked
            null, // closedAt
            0, // commentsCount
            now, // lastSyncAt
            now, // createdAt
            now, // updatedAt
            assignee.getId(),
            repo.getId(),
            null, // milestoneId
            null, // mergedAt
            isDraft, // isDraft
            false, // isMerged
            1,
            10,
            5,
            3,
            null,
            null,
            null,
            "feature/gate",
            "main",
            "gatesha",
            "basesha",
            null
        );
        PullRequest pr = pullRequestRepository.findByRepositoryIdAndNumber(repo.getId(), 100).orElseThrow();

        // Attach relationships that the gate reads (these are on the Issue superclass)
        pr.setLabels(labels != null ? labels : Set.of());
        pr.setAssignees(assignees != null ? assignees : Set.of());
        pr.setRepository(repo);
        return pr;
    }

    @Nested
    @DisplayName("Practice matching")
    class PracticeMatching {

        @Test
        @DisplayName("matches practices by trigger event")
        void matchesByTriggerEvent() {
            createPractice("pr-quality", "PR Quality", List.of("PullRequestCreated"), true);
            PullRequest pr = createPullRequest(false, Set.of(), Set.of(assignee));

            GateDecision decision = gate.evaluate(pr, "PullRequestCreated");

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            var detect = (GateDecision.Detect) decision;
            assertThat(detect.matchedPractices()).hasSize(1);
            assertThat(detect.matchedPractices().get(0).getSlug()).isEqualTo("pr-quality");
        }

        @Test
        @DisplayName("excludes inactive practices")
        void excludesInactive() {
            createPractice("active-one", "Active", List.of("PullRequestCreated"), true);
            createPractice("inactive-one", "Inactive", List.of("PullRequestCreated"), false);
            PullRequest pr = createPullRequest(false, Set.of(), Set.of(assignee));

            GateDecision decision = gate.evaluate(pr, "PullRequestCreated");

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            var detect = (GateDecision.Detect) decision;
            assertThat(detect.matchedPractices()).hasSize(1);
            assertThat(detect.matchedPractices().get(0).getSlug()).isEqualTo("active-one");
        }

        @Test
        @DisplayName("excludes mismatched trigger events")
        void excludesMismatchedEvents() {
            createPractice("review-only", "Review Only", List.of("ReviewSubmitted"), true);
            PullRequest pr = createPullRequest(false, Set.of(), Set.of(assignee));

            GateDecision decision = gate.evaluate(pr, "PullRequestCreated");

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
        }

        @Test
        @DisplayName("matches multiple practices")
        void matchesMultiple() {
            createPractice("practice-a", "Practice A", List.of("PullRequestCreated"), true);
            createPractice("practice-b", "Practice B", List.of("PullRequestCreated", "ReviewSubmitted"), true);
            createPractice("practice-c", "Practice C", List.of("ReviewSubmitted"), true);
            PullRequest pr = createPullRequest(false, Set.of(), Set.of(assignee));

            GateDecision decision = gate.evaluate(pr, "PullRequestCreated");

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            var detect = (GateDecision.Detect) decision;
            assertThat(detect.matchedPractices()).hasSize(2);
            assertThat(detect.matchedPractices())
                .extracting(Practice::getSlug)
                .containsExactlyInAnyOrder("practice-a", "practice-b");
        }
    }

    @Nested
    @DisplayName("Gate skips")
    class GateSkips {

        @Test
        @DisplayName("skips when no-ai-review label present")
        void skipsNoAiReviewLabel() {
            createPractice("labeled", "Labeled", List.of("PullRequestCreated"), true);
            Label label = new Label();
            label.setName("no-ai-review");
            label.setColor("ff0000");
            label.setNativeId(9001L);
            label.setProvider(provider);
            label = labelRepository.save(label);
            PullRequest pr = createPullRequest(false, Set.of(label), Set.of(assignee));

            GateDecision decision = gate.evaluate(pr, "PullRequestCreated");

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).contains("label");
        }

        @Test
        @DisplayName("skips when no agent config exists")
        void skipsNoConfig() {
            agentConfigRepository.deleteAll();
            createPractice("no-config", "No Config", List.of("PullRequestCreated"), true);
            PullRequest pr = createPullRequest(false, Set.of(), Set.of(assignee));

            GateDecision decision = gate.evaluate(pr, "PullRequestCreated");

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).contains("agent config");
        }

        @Test
        @DisplayName("skips when no practices match trigger event")
        void skipsNoMatchingPractices() {
            createPractice("wrong-event", "Wrong Event", List.of("ReviewSubmitted"), true);
            PullRequest pr = createPullRequest(false, Set.of(), Set.of(assignee));

            GateDecision decision = gate.evaluate(pr, "PullRequestCreated");

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).contains("practices");
        }

        @Test
        @DisplayName("skips draft PR when skipDrafts is configured")
        void skipsDraft() {
            createPractice("draft-skip", "Draft Skip", List.of("PullRequestCreated"), true);
            PullRequest pr = createPullRequest(true, Set.of(), Set.of(assignee));

            GateDecision decision = gate.evaluate(pr, "PullRequestCreated");

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).contains("draft");
        }
    }

    @Nested
    @DisplayName("Workspace resolution")
    class WorkspaceResolution {

        @Test
        @DisplayName("resolves workspace from repository owner (heuristic)")
        void resolvesFromRepoOwner() {
            createPractice("resolved", "Resolved", List.of("PullRequestCreated"), true);
            PullRequest pr = createPullRequest(false, Set.of(), Set.of(assignee));

            GateDecision decision = gate.evaluate(pr, "PullRequestCreated");

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            var detect = (GateDecision.Detect) decision;
            assertThat(detect.workspace().getId()).isEqualTo(workspace.getId());
        }

        @Test
        @DisplayName("skips when workspace cannot be resolved")
        void skipsUnresolvableWorkspace() {
            createPractice("orphan", "Orphan", List.of("PullRequestCreated"), true);

            // Create a repo with unknown owner
            Repository unknownRepo = new Repository();
            unknownRepo.setNativeId(3099L);
            unknownRepo.setProvider(provider);
            unknownRepo.setName("unknown-repo");
            unknownRepo.setNameWithOwner("unknown-org/unknown-repo");
            unknownRepo.setHtmlUrl("https://github.com/unknown-org/unknown-repo");
            unknownRepo.setDefaultBranch("main");
            unknownRepo = repositoryRepository.save(unknownRepo);

            PullRequest pr = createPullRequest(false, Set.of(), Set.of(assignee));
            pr.setRepository(unknownRepo);

            GateDecision decision = gate.evaluate(pr, "PullRequestCreated");

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).contains("workspace");
        }
    }
}
