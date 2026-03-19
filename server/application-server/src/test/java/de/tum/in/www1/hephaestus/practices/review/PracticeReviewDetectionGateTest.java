package de.tum.in.www1.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import de.tum.in.www1.hephaestus.agent.config.AgentConfigRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.practices.model.Practice;
import de.tum.in.www1.hephaestus.practices.spi.UserRoleChecker;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceResolver;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * Unit tests for {@link PracticeReviewDetectionGate}.
 * <p>
 * Tests each gate check individually and verifies ordering guarantees
 * (cheap checks prevent expensive DB/network calls).
 */
class PracticeReviewDetectionGateTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TRIGGER_EVENT = "PullRequestCreated";
    private static final String PRACTICE_REVIEW_ROLE = "run_practice_review";
    private static final Long WORKSPACE_ID = 1L;
    private static final Long PR_ID = 42L;

    @Mock
    private UserRoleChecker userRoleChecker;

    @Mock
    private AgentConfigRepository agentConfigRepository;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private WorkspaceResolver workspaceResolver;

    private PracticeReviewDetectionGate gate;

    @BeforeEach
    void setUp() {
        PracticeReviewProperties properties = new PracticeReviewProperties(false, true, 5);
        gate = new PracticeReviewDetectionGate(
            properties,
            userRoleChecker,
            agentConfigRepository,
            practiceRepository,
            workspaceResolver
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private PullRequest createPullRequest() {
        PullRequest pr = new PullRequest();
        pr.setId(PR_ID);
        pr.setLabels(new HashSet<>());
        pr.setAssignees(new HashSet<>());
        pr.setDraft(false);

        Repository repo = new Repository();
        repo.setNameWithOwner("ls1intum/Hephaestus");
        pr.setRepository(repo);

        return pr;
    }

    private Label createLabel(String name) {
        Label label = new Label();
        label.setName(name);
        return label;
    }

    private User createUser(String login) {
        User user = new User();
        user.setLogin(login);
        return user;
    }

    private Workspace createWorkspace() {
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        workspace.setWorkspaceSlug("test-workspace");
        return workspace;
    }

    private Practice createPractice(String... triggerEvents) {
        Practice practice = new Practice();
        ArrayNode events = MAPPER.createArrayNode();
        for (String event : triggerEvents) {
            events.add(event);
        }
        practice.setTriggerEvents(events);
        practice.setActive(true);
        return practice;
    }

    /** Sets up mocks through all gate checks to reach DETECT. Uses explicit mock setup per step. */
    private Workspace setupThroughPracticeMatching(PullRequest pr, Practice... practices) {
        Workspace workspace = createWorkspace();
        when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.of(workspace));
        when(agentConfigRepository.existsByWorkspaceIdAndEnabledTrue(WORKSPACE_ID)).thenReturn(true);
        when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(List.of(practices));
        return workspace;
    }

    // ── Gate Check Tests ────────────────────────────────────────────────────

    @Nested
    @DisplayName("1. Label Gate")
    class LabelGateTests {

        @Test
        @DisplayName("Should SKIP when PR has no-ai-review label")
        void skipWhenNoAiReviewLabel() {
            PullRequest pr = createPullRequest();
            pr.getLabels().add(createLabel("no-ai-review"));

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("label:no-ai-review");
        }

        @Test
        @DisplayName("Label skip should prevent any DB calls")
        void labelSkipPreventsDbCalls() {
            PullRequest pr = createPullRequest();
            pr.getLabels().add(createLabel("no-ai-review"));

            gate.evaluate(pr, TRIGGER_EVENT);

            verifyNoInteractions(workspaceResolver, agentConfigRepository, practiceRepository, userRoleChecker);
        }

        @Test
        @DisplayName("Label skip takes precedence over runForAllUsers=true")
        void labelSkipOverridesRunForAll() {
            PracticeReviewProperties runForAllProps = new PracticeReviewProperties(true, true, 5);
            PracticeReviewDetectionGate runForAllGate = new PracticeReviewDetectionGate(
                runForAllProps,
                userRoleChecker,
                agentConfigRepository,
                practiceRepository,
                workspaceResolver
            );

            PullRequest pr = createPullRequest();
            pr.getLabels().add(createLabel("no-ai-review"));

            GateDecision decision = runForAllGate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
        }

        @Test
        @DisplayName("Should SKIP when label matches case-insensitively")
        void skipWhenLabelMatchesCaseInsensitive() {
            PullRequest pr = createPullRequest();
            pr.getLabels().add(createLabel("No-AI-Review"));

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("label:no-ai-review");
        }

        @Test
        @DisplayName("Should SKIP when no-ai-review is among multiple labels")
        void skipWhenNoAiReviewAmongMultipleLabels() {
            PullRequest pr = createPullRequest();
            pr.getLabels().add(createLabel("enhancement"));
            pr.getLabels().add(createLabel("no-ai-review"));
            pr.getLabels().add(createLabel("bug"));

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("label:no-ai-review");
        }

        @Test
        @DisplayName("Should handle null labels gracefully")
        void handleNullLabels() {
            PullRequest pr = createPullRequest();
            pr.setLabels(null);
            // Set up mocks so the gate progresses past label check to workspace resolution
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.empty());

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT);

            // Should not throw, should progress past label check
            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no workspace");
        }
    }

    @Nested
    @DisplayName("2. Draft Gate")
    class DraftGateTests {

        @Test
        @DisplayName("Should SKIP when PR is draft and skipDrafts=true")
        void skipDraftPr() {
            PullRequest pr = createPullRequest();
            pr.setDraft(true);

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("draft PR");
        }

        @Test
        @DisplayName("Should continue when PR is draft but skipDrafts=false")
        void continueWhenSkipDraftsDisabled() {
            PracticeReviewProperties noSkipProps = new PracticeReviewProperties(false, false, 5);
            PracticeReviewDetectionGate noSkipGate = new PracticeReviewDetectionGate(
                noSkipProps,
                userRoleChecker,
                agentConfigRepository,
                practiceRepository,
                workspaceResolver
            );

            PullRequest pr = createPullRequest();
            pr.setDraft(true);
            // Set up enough mocks to progress past draft gate (workspace will fail -> SKIP)
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.empty());

            GateDecision decision = noSkipGate.evaluate(pr, TRIGGER_EVENT);

            // Should NOT be "draft PR" — draft gate was bypassed
            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no workspace");
        }

        @Test
        @DisplayName("Draft skip should prevent any DB calls")
        void draftSkipPreventsDbCalls() {
            PullRequest pr = createPullRequest();
            pr.setDraft(true);

            gate.evaluate(pr, TRIGGER_EVENT);

            verifyNoInteractions(workspaceResolver, agentConfigRepository, practiceRepository, userRoleChecker);
        }
    }

    @Nested
    @DisplayName("3. Workspace Resolution Gate")
    class WorkspaceResolutionTests {

        @Test
        @DisplayName("Should SKIP when workspace cannot be resolved")
        void skipWhenNoWorkspace() {
            PullRequest pr = createPullRequest();
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.empty());

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no workspace");
        }

        @Test
        @DisplayName("Should SKIP when PR has null repository")
        void skipWhenNullRepository() {
            PullRequest pr = createPullRequest();
            pr.setRepository(null);
            when(workspaceResolver.resolveForRepository(null)).thenReturn(Optional.empty());

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no workspace");
        }
    }

    @Nested
    @DisplayName("4. Agent Config Gate")
    class AgentConfigGateTests {

        @Test
        @DisplayName("Should SKIP when no enabled agent config exists")
        void skipWhenNoEnabledAgentConfig() {
            PullRequest pr = createPullRequest();
            Workspace workspace = createWorkspace();
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.existsByWorkspaceIdAndEnabledTrue(WORKSPACE_ID)).thenReturn(false);

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no enabled agent config");
        }
    }

    @Nested
    @DisplayName("5. Practice Matching Gate")
    class PracticeMatchingTests {

        @Test
        @DisplayName("Should SKIP when no active practices match trigger event")
        void skipWhenNoMatchingPractices() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice("ReviewSubmitted");
            setupThroughPracticeMatching(pr, practice);

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no matching practices");
        }

        @Test
        @DisplayName("Should handle practice with null triggerEvents gracefully")
        void handleNullTriggerEvents() {
            PullRequest pr = createPullRequest();
            Workspace workspace = createWorkspace();
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.existsByWorkspaceIdAndEnabledTrue(WORKSPACE_ID)).thenReturn(true);

            Practice practice = new Practice();
            practice.setTriggerEvents(null);
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(List.of(practice));

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no matching practices");
        }

        @Test
        @DisplayName("Should return all matching practices in Detect result")
        void returnsAllMatchingPractices() {
            PullRequest pr = createPullRequest();
            Practice matching1 = createPractice(TRIGGER_EVENT, "ReviewSubmitted");
            Practice matching2 = createPractice(TRIGGER_EVENT);
            Practice nonMatching = createPractice("ReviewSubmitted");
            Workspace workspace = createWorkspace();
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.of(workspace));
            when(agentConfigRepository.existsByWorkspaceIdAndEnabledTrue(WORKSPACE_ID)).thenReturn(true);
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(
                List.of(matching1, matching2, nonMatching)
            );

            User assignee = createUser("test-user");
            pr.setAssignees(Set.of(assignee));
            when(userRoleChecker.isHealthy()).thenReturn(true);
            when(userRoleChecker.hasRole("test-user", PRACTICE_REVIEW_ROLE)).thenReturn(true);

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            GateDecision.Detect detect = (GateDecision.Detect) decision;
            assertThat(detect.matchedPractices()).hasSize(2);
            assertThat(detect.matchedPractices()).containsExactly(matching1, matching2);
        }
    }

    @Nested
    @DisplayName("6. Run-for-All Bypass")
    class RunForAllTests {

        @Test
        @DisplayName("Should DETECT without role check when runForAllUsers=true")
        void detectWhenRunForAllUsers() {
            PracticeReviewProperties runForAllProps = new PracticeReviewProperties(true, true, 5);
            PracticeReviewDetectionGate runForAllGate = new PracticeReviewDetectionGate(
                runForAllProps,
                userRoleChecker,
                agentConfigRepository,
                practiceRepository,
                workspaceResolver
            );

            PullRequest pr = createPullRequest();
            Practice practice = createPractice(TRIGGER_EVENT);
            setupThroughPracticeMatching(pr, practice);

            GateDecision decision = runForAllGate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            GateDecision.Detect detect = (GateDecision.Detect) decision;
            assertThat(detect.workspace().getId()).isEqualTo(WORKSPACE_ID);
            assertThat(detect.matchedPractices()).containsExactly(practice);
            // Verify role checker was NEVER consulted
            verify(userRoleChecker, never()).hasRole(anyString(), anyString());
            verify(userRoleChecker, never()).isHealthy();
        }
    }

    @Nested
    @DisplayName("7. Assignee Gate")
    class AssigneeGateTests {

        @Test
        @DisplayName("Should SKIP when PR has no assignees")
        void skipWhenNoAssignee() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(TRIGGER_EVENT);
            setupThroughPracticeMatching(pr, practice);
            // No assignees set (empty set from createPullRequest)

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no assignee");
        }

        @Test
        @DisplayName("Should SKIP when PR has null assignees")
        void skipWhenNullAssignees() {
            PullRequest pr = createPullRequest();
            pr.setAssignees(null);
            Practice practice = createPractice(TRIGGER_EVENT);
            setupThroughPracticeMatching(pr, practice);

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no assignee");
        }
    }

    @Nested
    @DisplayName("8. Circuit Breaker Gate")
    class CircuitBreakerTests {

        @Test
        @DisplayName("Should SKIP when Keycloak circuit breaker is open")
        void skipWhenCircuitBreakerOpen() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(TRIGGER_EVENT);
            setupThroughPracticeMatching(pr, practice);

            User assignee = createUser("test-user");
            pr.setAssignees(Set.of(assignee));

            when(userRoleChecker.isHealthy()).thenReturn(false);

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("keycloak circuit breaker open");
            verify(userRoleChecker, never()).hasRole(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("9. Role Check Gate")
    class RoleCheckTests {

        @Test
        @DisplayName("Should DETECT when assignee has run_practice_review role")
        void detectWhenHasRole() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(TRIGGER_EVENT);
            Workspace workspace = setupThroughPracticeMatching(pr, practice);

            User assignee = createUser("test-user");
            pr.setAssignees(Set.of(assignee));
            when(userRoleChecker.isHealthy()).thenReturn(true);
            when(userRoleChecker.hasRole("test-user", PRACTICE_REVIEW_ROLE)).thenReturn(true);

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            GateDecision.Detect detect = (GateDecision.Detect) decision;
            assertThat(detect.workspace().getId()).isEqualTo(WORKSPACE_ID);
            assertThat(detect.matchedPractices()).containsExactly(practice);
        }

        @Test
        @DisplayName("Should SKIP when assignee is missing run_practice_review role")
        void skipWhenMissingRole() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(TRIGGER_EVENT);
            setupThroughPracticeMatching(pr, practice);

            User assignee = createUser("test-user");
            pr.setAssignees(Set.of(assignee));
            when(userRoleChecker.isHealthy()).thenReturn(true);
            when(userRoleChecker.hasRole("test-user", PRACTICE_REVIEW_ROLE)).thenReturn(false);

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo(
                "no assignee with role: " + PRACTICE_REVIEW_ROLE
            );
        }

        @Test
        @DisplayName("Should DETECT if ANY assignee has the role (multi-assignee)")
        void detectWhenAnyAssigneeHasRole() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(TRIGGER_EVENT);
            Workspace workspace = setupThroughPracticeMatching(pr, practice);

            User userWithRole = createUser("user-with-role");
            User userWithoutRole = createUser("user-without-role");
            pr.setAssignees(Set.of(userWithRole, userWithoutRole));
            when(userRoleChecker.isHealthy()).thenReturn(true);
            when(userRoleChecker.hasRole("user-with-role", PRACTICE_REVIEW_ROLE)).thenReturn(true);
            // Lenient: HashSet iteration order is nondeterministic, so this mock may not be reached
            lenient().when(userRoleChecker.hasRole("user-without-role", PRACTICE_REVIEW_ROLE)).thenReturn(false);

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            GateDecision.Detect detect = (GateDecision.Detect) decision;
            assertThat(detect.workspace().getId()).isEqualTo(WORKSPACE_ID);
            assertThat(detect.matchedPractices()).containsExactly(practice);
        }

        @Test
        @DisplayName("Should SKIP when ALL assignees lack the role (multi-assignee)")
        void skipWhenNoAssigneeHasRole() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(TRIGGER_EVENT);
            setupThroughPracticeMatching(pr, practice);

            User user1 = createUser("user-1");
            User user2 = createUser("user-2");
            pr.setAssignees(Set.of(user1, user2));
            when(userRoleChecker.isHealthy()).thenReturn(true);
            when(userRoleChecker.hasRole("user-1", PRACTICE_REVIEW_ROLE)).thenReturn(false);
            when(userRoleChecker.hasRole("user-2", PRACTICE_REVIEW_ROLE)).thenReturn(false);

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo(
                "no assignee with role: " + PRACTICE_REVIEW_ROLE
            );
        }

        @Test
        @DisplayName("Should SKIP gracefully when hasRole throws exception")
        void skipWhenRoleCheckThrowsException() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(TRIGGER_EVENT);
            setupThroughPracticeMatching(pr, practice);

            User assignee = createUser("test-user");
            pr.setAssignees(Set.of(assignee));
            when(userRoleChecker.isHealthy()).thenReturn(true);
            when(userRoleChecker.hasRole("test-user", PRACTICE_REVIEW_ROLE)).thenThrow(
                new RuntimeException("Connection refused")
            );

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("role check failed");
        }
    }

    @Nested
    @DisplayName("Happy Path")
    class HappyPathTests {

        @Test
        @DisplayName("Should return Detect with workspace and matched practices when all checks pass")
        void fullHappyPath() {
            PullRequest pr = createPullRequest();
            pr.getLabels().add(createLabel("enhancement"));
            Practice practice = createPractice(TRIGGER_EVENT);
            Workspace workspace = setupThroughPracticeMatching(pr, practice);

            User assignee = createUser("test-user");
            pr.setAssignees(Set.of(assignee));
            when(userRoleChecker.isHealthy()).thenReturn(true);
            when(userRoleChecker.hasRole("test-user", PRACTICE_REVIEW_ROLE)).thenReturn(true);

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT);

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            GateDecision.Detect detect = (GateDecision.Detect) decision;
            assertThat(detect.workspace().getId()).isEqualTo(WORKSPACE_ID);
            assertThat(detect.workspace().getWorkspaceSlug()).isEqualTo("test-workspace");
            assertThat(detect.matchedPractices()).containsExactly(practice);
        }

        @Test
        @DisplayName("Detect should throw on null workspace")
        void detectThrowsOnNullWorkspace() {
            Practice practice = createPractice(TRIGGER_EVENT);

            org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () ->
                new GateDecision.Detect(null, List.of(practice))
            );
        }

        @Test
        @DisplayName("Detect should throw on null matchedPractices")
        void detectThrowsOnNullMatchedPractices() {
            Workspace workspace = createWorkspace();

            org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () ->
                new GateDecision.Detect(workspace, null)
            );
        }

        @Test
        @DisplayName("Detect matchedPractices list should be unmodifiable")
        void detectMatchedPracticesIsUnmodifiable() {
            Practice practice = createPractice(TRIGGER_EVENT);
            Workspace workspace = createWorkspace();

            GateDecision.Detect detect = new GateDecision.Detect(workspace, List.of(practice));

            org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () ->
                detect.matchedPractices().add(new Practice())
            );
        }
    }
}
