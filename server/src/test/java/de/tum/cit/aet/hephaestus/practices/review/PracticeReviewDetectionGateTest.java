package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.scm.domain.label.Label;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.spi.AgentConfigChecker;
import de.tum.cit.aet.hephaestus.practices.spi.UserRoleChecker;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceFeatures;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceResolver;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

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
    private AgentConfigChecker agentConfigChecker;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private WorkspaceResolver workspaceResolver;

    private PracticeReviewDetectionGate gate;

    @BeforeEach
    void setUp() {
        PracticeReviewProperties properties = new PracticeReviewProperties(false, true, false, "", 15);
        gate = new PracticeReviewDetectionGate(
            properties,
            userRoleChecker,
            agentConfigChecker,
            practiceRepository,
            workspaceResolver
        );
    }

    // Helpers

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

    private static final long TEST_PROVIDER_ID = 1L;

    /** Stable, positive provider-native id per login (single source of truth for the test identity). */
    private static long nativeIdOf(String login) {
        return Math.abs((long) login.hashCode()) + 1;
    }

    /** The {@code IdentityLink.subject} the gate keys on for this user (== {@code String.valueOf(nativeId)}). */
    private static String subjectOf(String login) {
        return String.valueOf(nativeIdOf(login));
    }

    private User createUser(String login) {
        User user = new User();
        user.setLogin(login);
        // The gate resolves the role by the stable (gitProviderId, subject) identity, not the login.
        user.setNativeId(nativeIdOf(login));
        GitProvider provider = new GitProvider();
        provider.setId(TEST_PROVIDER_ID);
        user.setProvider(provider);
        return user;
    }

    private Workspace createWorkspace() {
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        workspace.setWorkspaceSlug("test-workspace");
        WorkspaceFeatures features = new WorkspaceFeatures();
        features.setPracticesEnabled(true);
        features.setPracticeReviewAutoTriggerEnabled(true);
        features.setPracticeReviewManualTriggerEnabled(true);
        workspace.setFeatures(features);
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
        when(agentConfigChecker.hasEnabledConfig(WORKSPACE_ID)).thenReturn(true);
        when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(List.of(practices));
        return workspace;
    }

    // Gate Check Tests

    @Nested
    class DraftGateTests {

        @Test
        void skipDraftPr() {
            PullRequest pr = createPullRequest();
            pr.setDraft(true);
            // The draft gate runs after workspace resolution so it can read the per-workspace
            // skipDrafts override (which inherits the property default of true here).
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(
                Optional.of(createWorkspace())
            );

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("draft PR");
        }

        @Test
        void continueWhenSkipDraftsDisabled() {
            PracticeReviewProperties noSkipProps = new PracticeReviewProperties(false, false, false, "", 15);
            PracticeReviewDetectionGate noSkipGate = new PracticeReviewDetectionGate(
                noSkipProps,
                userRoleChecker,
                agentConfigChecker,
                practiceRepository,
                workspaceResolver
            );

            PullRequest pr = createPullRequest();
            pr.setDraft(true);
            // Set up enough mocks to progress past draft gate (workspace will fail -> SKIP)
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.empty());

            GateDecision decision = noSkipGate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            // Should NOT be "draft PR" — draft gate was bypassed
            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no workspace");
        }

        @Test
        void draftSkipShortCircuitsBeforeExpensiveChecks() {
            PullRequest pr = createPullRequest();
            pr.setDraft(true);
            // The draft gate runs after (cheap) workspace resolution but before the expensive
            // agent-config / practice / role checks — assert those are never touched.
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(
                Optional.of(createWorkspace())
            );

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("draft PR");
            verifyNoInteractions(agentConfigChecker, practiceRepository, userRoleChecker);
        }
    }

    @Nested
    class WorkspaceResolutionTests {

        @Test
        void skipWhenNoWorkspace() {
            PullRequest pr = createPullRequest();
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.empty());

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no workspace");
        }

        @Test
        void skipWhenNullRepository() {
            PullRequest pr = createPullRequest();
            pr.setRepository(null);
            when(workspaceResolver.resolveForRepository(null)).thenReturn(Optional.empty());

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no workspace");
        }
    }

    @Nested
    class PracticesEnabledTests {

        @Test
        void skipWhenPracticesDisabled() {
            PullRequest pr = createPullRequest();
            Workspace workspace = createWorkspace();
            workspace.getFeatures().setPracticesEnabled(false);
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.of(workspace));

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("practices disabled for workspace");
            verifyNoInteractions(agentConfigChecker, practiceRepository, userRoleChecker);
        }
    }

    @Nested
    class TriggerModeTests {

        @Test
        void skipWhenAutoTriggerDisabled() {
            PullRequest pr = createPullRequest();
            Workspace workspace = createWorkspace();
            workspace.getFeatures().setPracticeReviewAutoTriggerEnabled(false);
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.of(workspace));

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("auto-trigger disabled for workspace");
            verifyNoInteractions(agentConfigChecker, practiceRepository, userRoleChecker);
        }

        @Test
        void skipWhenManualTriggerDisabled() {
            PullRequest pr = createPullRequest();
            Workspace workspace = createWorkspace();
            workspace.getFeatures().setPracticeReviewManualTriggerEnabled(false);
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.of(workspace));

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.MANUAL);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("manual trigger disabled for workspace");
            verifyNoInteractions(agentConfigChecker, practiceRepository, userRoleChecker);
        }

        @Test
        void skipWhenBothTriggersDisabled() {
            PullRequest pr = createPullRequest();
            Workspace workspace = createWorkspace();
            workspace.getFeatures().setPracticeReviewAutoTriggerEnabled(false);
            workspace.getFeatures().setPracticeReviewManualTriggerEnabled(false);
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.of(workspace));

            GateDecision autoDecision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);
            GateDecision manualDecision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.MANUAL);

            assertThat(autoDecision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) autoDecision).reason()).isEqualTo("auto-trigger disabled for workspace");
            assertThat(manualDecision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) manualDecision).reason()).isEqualTo(
                "manual trigger disabled for workspace"
            );
        }

        @Test
        void continueWhenAutoTriggerDisabledButModeIsManual() {
            PullRequest pr = createPullRequest();
            Workspace workspace = createWorkspace();
            workspace.getFeatures().setPracticeReviewAutoTriggerEnabled(false);
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.of(workspace));
            when(agentConfigChecker.hasEnabledConfig(WORKSPACE_ID)).thenReturn(false);

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.MANUAL);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no enabled agent config");
        }

        @Test
        void continueWhenManualTriggerDisabledButModeIsAuto() {
            PullRequest pr = createPullRequest();
            Workspace workspace = createWorkspace();
            workspace.getFeatures().setPracticeReviewManualTriggerEnabled(false);
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.of(workspace));
            when(agentConfigChecker.hasEnabledConfig(WORKSPACE_ID)).thenReturn(false);

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no enabled agent config");
        }
    }

    @Nested
    class AgentConfigGateTests {

        @Test
        void skipWhenNoEnabledAgentConfig() {
            PullRequest pr = createPullRequest();
            Workspace workspace = createWorkspace();
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.of(workspace));
            when(agentConfigChecker.hasEnabledConfig(WORKSPACE_ID)).thenReturn(false);

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no enabled agent config");
        }
    }

    @Nested
    class PracticeMatchingTests {

        @Test
        void skipWhenNoMatchingPractices() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice("ReviewSubmitted");
            setupThroughPracticeMatching(pr, practice);

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no matching practices");
        }

        @Test
        void handleNullTriggerEvents() {
            PullRequest pr = createPullRequest();
            Workspace workspace = createWorkspace();
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.of(workspace));
            when(agentConfigChecker.hasEnabledConfig(WORKSPACE_ID)).thenReturn(true);

            Practice practice = new Practice();
            practice.setTriggerEvents(null);
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(List.of(practice));

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no matching practices");
        }

        @Test
        void returnsAllMatchingPractices() {
            PullRequest pr = createPullRequest();
            Practice matching1 = createPractice(TRIGGER_EVENT, "ReviewSubmitted");
            Practice matching2 = createPractice(TRIGGER_EVENT);
            Practice nonMatching = createPractice("ReviewSubmitted");
            Workspace workspace = createWorkspace();
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.of(workspace));
            when(agentConfigChecker.hasEnabledConfig(WORKSPACE_ID)).thenReturn(true);
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(WORKSPACE_ID)).thenReturn(
                List.of(matching1, matching2, nonMatching)
            );

            User assignee = createUser("test-user");
            pr.setAssignees(Set.of(assignee));
            when(userRoleChecker.isHealthy()).thenReturn(true);
            when(userRoleChecker.hasRole(TEST_PROVIDER_ID, subjectOf("test-user"), PRACTICE_REVIEW_ROLE)).thenReturn(
                true
            );

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            GateDecision.Detect detect = (GateDecision.Detect) decision;
            assertThat(detect.matchedPractices()).hasSize(2);
            assertThat(detect.matchedPractices()).containsExactly(matching1, matching2);
        }
    }

    @Nested
    class RunForAllTests {

        @Test
        void detectWhenRunForAllUsers() {
            PracticeReviewProperties runForAllProps = new PracticeReviewProperties(true, true, false, "", 15);
            PracticeReviewDetectionGate runForAllGate = new PracticeReviewDetectionGate(
                runForAllProps,
                userRoleChecker,
                agentConfigChecker,
                practiceRepository,
                workspaceResolver
            );

            PullRequest pr = createPullRequest();
            Practice practice = createPractice(TRIGGER_EVENT);
            setupThroughPracticeMatching(pr, practice);

            GateDecision decision = runForAllGate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            GateDecision.Detect detect = (GateDecision.Detect) decision;
            assertThat(detect.workspace().getId()).isEqualTo(WORKSPACE_ID);
            assertThat(detect.matchedPractices()).containsExactly(practice);
            // Verify role checker was NEVER consulted
            verify(userRoleChecker, never()).hasRole(anyLong(), anyString(), anyString());
            verify(userRoleChecker, never()).isHealthy();
        }
    }

    @Nested
    class AssigneeGateTests {

        @Test
        @DisplayName("Should SKIP when PR has no assignees")
        void skipWhenNoAssignee() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(TRIGGER_EVENT);
            setupThroughPracticeMatching(pr, practice);
            // No assignees set (empty set from createPullRequest)

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no assignee");
        }

        @Test
        void skipWhenNullAssignees() {
            PullRequest pr = createPullRequest();
            pr.setAssignees(null);
            Practice practice = createPractice(TRIGGER_EVENT);
            setupThroughPracticeMatching(pr, practice);

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no assignee");
        }
    }

    @Nested
    class RoleCheckerHealthTests {

        @Test
        void skipWhenRoleCheckerUnhealthy() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(TRIGGER_EVENT);
            setupThroughPracticeMatching(pr, practice);

            User assignee = createUser("test-user");
            pr.setAssignees(Set.of(assignee));

            when(userRoleChecker.isHealthy()).thenReturn(false);

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("role checker unhealthy");
            verify(userRoleChecker, never()).hasRole(anyLong(), anyString(), anyString());
        }
    }

    @Nested
    class RoleCheckTests {

        @Test
        void detectWhenHasRole() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(TRIGGER_EVENT);
            Workspace workspace = setupThroughPracticeMatching(pr, practice);

            User assignee = createUser("test-user");
            pr.setAssignees(Set.of(assignee));
            when(userRoleChecker.isHealthy()).thenReturn(true);
            when(userRoleChecker.hasRole(TEST_PROVIDER_ID, subjectOf("test-user"), PRACTICE_REVIEW_ROLE)).thenReturn(
                true
            );

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            GateDecision.Detect detect = (GateDecision.Detect) decision;
            assertThat(detect.workspace().getId()).isEqualTo(WORKSPACE_ID);
            assertThat(detect.matchedPractices()).containsExactly(practice);
        }

        @Test
        void skipWhenMissingRole() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(TRIGGER_EVENT);
            setupThroughPracticeMatching(pr, practice);

            User assignee = createUser("test-user");
            pr.setAssignees(Set.of(assignee));
            when(userRoleChecker.isHealthy()).thenReturn(true);
            when(userRoleChecker.hasRole(TEST_PROVIDER_ID, subjectOf("test-user"), PRACTICE_REVIEW_ROLE)).thenReturn(
                false
            );

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo(
                "no assignee with role: " + PRACTICE_REVIEW_ROLE
            );
        }

        @Test
        void detectWhenAnyAssigneeHasRole() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(TRIGGER_EVENT);
            Workspace workspace = setupThroughPracticeMatching(pr, practice);

            User userWithRole = createUser("user-with-role");
            User userWithoutRole = createUser("user-without-role");
            pr.setAssignees(Set.of(userWithRole, userWithoutRole));
            when(userRoleChecker.isHealthy()).thenReturn(true);
            when(
                userRoleChecker.hasRole(TEST_PROVIDER_ID, subjectOf("user-with-role"), PRACTICE_REVIEW_ROLE)
            ).thenReturn(true);
            // Lenient: HashSet iteration order is nondeterministic, so this mock may not be reached
            lenient()
                .when(userRoleChecker.hasRole(TEST_PROVIDER_ID, subjectOf("user-without-role"), PRACTICE_REVIEW_ROLE))
                .thenReturn(false);

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            GateDecision.Detect detect = (GateDecision.Detect) decision;
            assertThat(detect.workspace().getId()).isEqualTo(WORKSPACE_ID);
            assertThat(detect.matchedPractices()).containsExactly(practice);
        }

        @Test
        void skipWhenNoAssigneeHasRole() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(TRIGGER_EVENT);
            setupThroughPracticeMatching(pr, practice);

            User user1 = createUser("user-1");
            User user2 = createUser("user-2");
            pr.setAssignees(Set.of(user1, user2));
            when(userRoleChecker.isHealthy()).thenReturn(true);
            when(userRoleChecker.hasRole(TEST_PROVIDER_ID, subjectOf("user-1"), PRACTICE_REVIEW_ROLE)).thenReturn(
                false
            );
            when(userRoleChecker.hasRole(TEST_PROVIDER_ID, subjectOf("user-2"), PRACTICE_REVIEW_ROLE)).thenReturn(
                false
            );

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo(
                "no assignee with role: " + PRACTICE_REVIEW_ROLE
            );
        }

        @Test
        void skipWhenRoleCheckThrowsException() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(TRIGGER_EVENT);
            setupThroughPracticeMatching(pr, practice);

            User assignee = createUser("test-user");
            pr.setAssignees(Set.of(assignee));
            when(userRoleChecker.isHealthy()).thenReturn(true);
            when(userRoleChecker.hasRole(TEST_PROVIDER_ID, subjectOf("test-user"), PRACTICE_REVIEW_ROLE)).thenThrow(
                new RuntimeException("Connection refused")
            );

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("role check failed");
        }
    }

    @Nested
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
            when(userRoleChecker.hasRole(TEST_PROVIDER_ID, subjectOf("test-user"), PRACTICE_REVIEW_ROLE)).thenReturn(
                true
            );

            GateDecision decision = gate.evaluate(pr, TRIGGER_EVENT, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            GateDecision.Detect detect = (GateDecision.Detect) decision;
            assertThat(detect.workspace().getId()).isEqualTo(WORKSPACE_ID);
            assertThat(detect.workspace().getWorkspaceSlug()).isEqualTo("test-workspace");
            assertThat(detect.matchedPractices()).containsExactly(practice);
        }

        @Test
        void detectThrowsOnNullWorkspace() {
            Practice practice = createPractice(TRIGGER_EVENT);

            org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () ->
                new GateDecision.Detect(null, List.of(practice))
            );
        }

        @Test
        void detectThrowsOnNullMatchedPractices() {
            Workspace workspace = createWorkspace();

            org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () ->
                new GateDecision.Detect(workspace, null)
            );
        }

        @Test
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
