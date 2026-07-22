package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.label.Label;
import de.tum.cit.aet.hephaestus.integration.scm.domain.label.LabelRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.spi.UserRoleChecker;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration test for {@link PracticeReviewDetectionGate} exercising real PostgreSQL
 * queries for workspace resolution, agent config checking, and JSONB practice matching.
 *
 * <p>Primary integration value: workspace resolver heuristic, agent config existence check,
 * and JSONB containment query ({@code triggerEvents @> ?}) against real PostgreSQL.
 *
 * <p>Mocks only the role-check SPI ({@link UserRoleChecker}).
 * Label/draft/assignee checks operate on the in-memory PR object passed to the gate
 * (matching production behavior where the PR is loaded once by the caller).
 */
class PracticeDetectionGateIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private PracticeReviewDetectionGate gate;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private AgentConfigRepository agentConfigRepository;

    @Autowired
    private WorkspaceLlmConnectionRepository workspaceLlmConnectionRepository;

    @Autowired
    private WorkspaceLlmModelRepository workspaceLlmModelRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private IdentityProviderRepository gitProviderRepository;

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
    private WorkspaceLlmModel workspaceModel;
    private Repository repo;
    private IdentityProvider provider;
    private User assignee;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();

        // Workspace with accountLogin = "org" so WorkspaceResolver heuristic matches "org/repo"
        workspace = WorkspaceTestFixtures.activeWorkspace("gate-test");
        workspace.setAccountLogin("org");
        workspace.getFeatures().setPracticesEnabled(true);
        workspace = workspaceRepository.save(workspace);

        WorkspaceLlmConnection connection = new WorkspaceLlmConnection();
        connection.setWorkspace(workspace);
        connection.setSlug("gate-connection");
        connection.setDisplayName("Gate connection");
        connection.setBaseUrl("https://api.openai.com");
        connection.setApiProtocol("openai-completions");
        connection.setEnabled(true);
        connection = workspaceLlmConnectionRepository.save(connection);

        workspaceModel = new WorkspaceLlmModel();
        workspaceModel.setWorkspace(workspace);
        workspaceModel.setConnection(connection);
        workspaceModel.setSlug("gate-model");
        workspaceModel.setDisplayName("Gate model");
        workspaceModel.setUpstreamModelId("gpt-5");
        workspaceModel.setEnabled(true);
        workspaceModel = workspaceLlmModelRepository.save(workspaceModel);

        // Enabled config backed by a live catalog model.
        AgentConfig config = new AgentConfig();
        config.setWorkspace(workspace);
        config.setName("gate-config");
        config.setEnabled(true);
        config.setLlmProvider(LlmProvider.ANTHROPIC);
        config.setWorkspaceModel(workspaceModel);
        config.setTimeoutSeconds(300);
        agentConfigRepository.save(config);

        provider = gitProviderRepository
            .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
            .orElseGet(() ->
                gitProviderRepository.save(new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com"))
            );

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
        // Role is resolved by the stable (gitProviderId, subject) identity; subject == User.nativeId.
        when(userRoleChecker.hasRole(anyLong(), eq(String.valueOf(assignee.getNativeId())), anyString())).thenReturn(
            true
        );
    }

    private Practice createPractice(String slug, String name, List<String> triggerEvents, boolean active) {
        Practice p = new Practice();
        p.setWorkspace(workspace);
        p.setSlug(slug);
        p.setName(name);
        p.setCriteria("Test " + slug);
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
            null,
            null // mergeCommitSha
        );
        PullRequest pr = pullRequestRepository.findByRepositoryIdAndNumber(repo.getId(), 100).orElseThrow();

        // Attach relationships that the gate reads (these are on the Issue superclass)
        pr.setLabels(labels != null ? labels : Set.of());
        pr.setAssignees(assignees != null ? assignees : Set.of());
        pr.setRepository(repo);
        return pr;
    }

    @Nested
    class PracticeMatching {

        @Test
        void matchesByTriggerEvent() {
            createPractice("pr-quality", "PR Quality", List.of("PullRequestCreated"), true);
            PullRequest pr = createPullRequest(false, Set.of(), Set.of(assignee));

            GateDecision decision = gate.evaluate(pr, "PullRequestCreated", TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            var detect = (GateDecision.Detect) decision;
            assertThat(detect.matchedPractices()).hasSize(1);
            assertThat(detect.matchedPractices().get(0).getSlug()).isEqualTo("pr-quality");
        }

        @Test
        void excludesInactive() {
            createPractice("active-one", "Active", List.of("PullRequestCreated"), true);
            createPractice("inactive-one", "Inactive", List.of("PullRequestCreated"), false);
            PullRequest pr = createPullRequest(false, Set.of(), Set.of(assignee));

            GateDecision decision = gate.evaluate(pr, "PullRequestCreated", TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            var detect = (GateDecision.Detect) decision;
            assertThat(detect.matchedPractices()).hasSize(1);
            assertThat(detect.matchedPractices().get(0).getSlug()).isEqualTo("active-one");
        }

        @Test
        void excludesMismatchedEvents() {
            createPractice("review-only", "Review Only", List.of("ReviewSubmitted"), true);
            PullRequest pr = createPullRequest(false, Set.of(), Set.of(assignee));

            GateDecision decision = gate.evaluate(pr, "PullRequestCreated", TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
        }

        @Test
        void matchesMultiple() {
            createPractice("practice-a", "Practice A", List.of("PullRequestCreated"), true);
            createPractice("practice-b", "Practice B", List.of("PullRequestCreated", "ReviewSubmitted"), true);
            createPractice("practice-c", "Practice C", List.of("ReviewSubmitted"), true);
            PullRequest pr = createPullRequest(false, Set.of(), Set.of(assignee));

            GateDecision decision = gate.evaluate(pr, "PullRequestCreated", TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            var detect = (GateDecision.Detect) decision;
            assertThat(detect.matchedPractices()).hasSize(2);
            assertThat(detect.matchedPractices())
                .extracting(Practice::getSlug)
                .containsExactlyInAnyOrder("practice-a", "practice-b");
        }
    }

    @Nested
    class GateSkips {

        @Test
        void skipsNoConfig() {
            agentConfigRepository.deleteAll();
            createPractice("no-config", "No Config", List.of("PullRequestCreated"), true);
            PullRequest pr = createPullRequest(false, Set.of(), Set.of(assignee));

            GateDecision decision = gate.evaluate(pr, "PullRequestCreated", TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            // No runnable practice config → gate skips before detection.
            assertThat(((GateDecision.Skip) decision).reason()).contains("no runnable practice config");
        }

        @Test
        void skipsUnavailableModelWithoutPoisoningTheReadTransaction() {
            workspaceModel.setEnabled(false);
            workspaceLlmModelRepository.saveAndFlush(workspaceModel);
            createPractice("unavailable-model", "Unavailable model", List.of("PullRequestCreated"), true);
            PullRequest pr = createPullRequest(false, Set.of(), Set.of(assignee));

            GateDecision decision = gate.evaluate(pr, "PullRequestCreated", TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).contains("no runnable practice config");
        }

        @Test
        void skipsNoMatchingPractices() {
            createPractice("wrong-event", "Wrong Event", List.of("ReviewSubmitted"), true);
            PullRequest pr = createPullRequest(false, Set.of(), Set.of(assignee));

            GateDecision decision = gate.evaluate(pr, "PullRequestCreated", TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).contains("practices");
        }

        @Test
        void skipsDraft() {
            createPractice("draft-skip", "Draft Skip", List.of("PullRequestCreated"), true);
            PullRequest pr = createPullRequest(true, Set.of(), Set.of(assignee));

            GateDecision decision = gate.evaluate(pr, "PullRequestCreated", TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).contains("draft");
        }
    }

    @Nested
    class WorkspaceResolution {

        @Test
        void resolvesFromRepoOwner() {
            createPractice("resolved", "Resolved", List.of("PullRequestCreated"), true);
            PullRequest pr = createPullRequest(false, Set.of(), Set.of(assignee));

            GateDecision decision = gate.evaluate(pr, "PullRequestCreated", TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            var detect = (GateDecision.Detect) decision;
            assertThat(detect.workspace().getId()).isEqualTo(workspace.getId());
        }

        @Test
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

            GateDecision decision = gate.evaluate(pr, "PullRequestCreated", TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).contains("workspace");
        }
    }
}
