package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.scm.domain.label.Label;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeBinding;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeReviewReadiness;
import de.tum.cit.aet.hephaestus.practices.spi.UserRoleChecker;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceFeatures;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceResolver;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class PracticeReviewDetectionGateTest extends BaseUnitTest {

    private static final SignalName SIGNAL = ScmSignals.PULL_REQUEST_OPENED;
    private static final String PRACTICE_REVIEW_ROLE = "run_practice_review";
    private static final Long WORKSPACE_ID = 1L;
    private static final Long PR_ID = 42L;

    @Mock
    private UserRoleChecker userRoleChecker;

    @Mock
    private PracticeReviewReadiness practiceDetectionReadiness;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private WorkspaceResolver workspaceResolver;

    private PracticeReviewDetectionGate gate;

    @BeforeEach
    void setUp() {
        PracticeReviewProperties properties = new PracticeReviewProperties(false, true, false, 15, false, false);
        gate = new PracticeReviewDetectionGate(
            properties,
            userRoleChecker,
            practiceDetectionReadiness,
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
        IdentityProvider provider = new IdentityProvider();
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

    private Practice createPractice(SignalName... signals) {
        Practice practice = new Practice();
        practice.setBindings(PracticeTestEvidence.bindings(signals));
        practice.setUsedInNewReviews(true);
        return practice;
    }

    /** A practice that also reviews an artifact still marked draft. */
    private Practice createDraftPractice(SignalName... signals) {
        Practice practice = new Practice();
        practice.setBindings(
            List.of(
                new PracticeBinding(List.of(signals), PracticeTestEvidence.needsFor(ArtifactKinds.PULL_REQUEST), true)
            )
        );
        practice.setUsedInNewReviews(true);
        return practice;
    }

    private Workspace setupThroughPracticeMatching(PullRequest pr, Practice... practices) {
        Workspace workspace = createWorkspace();
        when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.of(workspace));
        when(practiceDetectionReadiness.hasRunnableAgent(WORKSPACE_ID)).thenReturn(true);
        when(practiceRepository.findByWorkspaceIdAndUsedInNewReviewsTrue(WORKSPACE_ID)).thenReturn(List.of(practices));
        return workspace;
    }

    // Gate Check Tests

    /**
     * The draft question used to be a veto: one fleet-wide setting, checked before anything else, that
     * decided for every practice at once whether a draft was worth reviewing. It defaulted to skipping,
     * which meant the draft-specific criteria several shipped practices are largely made of could not be
     * reached at all — {@code ready-and-traceable-handoff} spends most of its rubric on how to judge a
     * draft handover, and none of it ever ran.
     *
     * <p>It is a per-binding fact now, because it is a property of the occasion rather than of the
     * workspace: judging how a change was handed over is exactly what one wants to say about a draft,
     * while judging how a merged change was reviewed cannot arise on one.
     */
    @Nested
    class DraftGateTests {

        @Test
        @DisplayName("a draft does not occasion a practice that did not ask for drafts")
        void skipDraftForAPracticeThatDoesNotWantThem() {
            PullRequest pr = createPullRequest();
            pr.setDraft(true);
            setupThroughPracticeMatching(pr, createPractice(SIGNAL));

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo(
                "no practices bound to this signal on drafts"
            );
        }

        @Test
        @DisplayName("a binding that asks for drafts reaches its draft-specific criteria")
        void detectDraftForAPracticeThatAsksForThem() {
            PullRequest pr = createPullRequest();
            pr.setDraft(true);
            Practice onDrafts = createDraftPractice(SIGNAL);
            Workspace workspace = setupThroughPracticeMatching(pr, onDrafts);
            workspace.getReviewSettings().applyPatch(true, null, null, null);

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            assertThat(((GateDecision.Detect) decision).matchedPractices()).containsExactly(onDrafts);
        }

        @Test
        @DisplayName("a draft admits only the practices that asked for it, not the whole set")
        void draftAdmitsOnlyTheBindingsThatAskedForIt() {
            PullRequest pr = createPullRequest();
            pr.setDraft(true);
            Practice onDrafts = createDraftPractice(SIGNAL);
            Workspace workspace = setupThroughPracticeMatching(pr, createPractice(SIGNAL), onDrafts);
            workspace.getReviewSettings().applyPatch(true, null, null, null);

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

            assertThat(((GateDecision.Detect) decision).matchedPractices()).containsExactly(onDrafts);
        }

        @Test
        @DisplayName("a practice that asks for drafts still reviews work that is not a draft")
        void draftBindingAlsoCoversNonDrafts() {
            PullRequest pr = createPullRequest();
            Practice onDrafts = createDraftPractice(SIGNAL);
            Workspace workspace = setupThroughPracticeMatching(pr, onDrafts);
            workspace.getReviewSettings().applyPatch(true, null, null, null);

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
        }
    }

    @Nested
    class WorkspaceResolutionTests {

        @Test
        void skipWhenNoWorkspace() {
            PullRequest pr = createPullRequest();
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.empty());

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no workspace");
        }

        @Test
        void skipWhenNullRepository() {
            PullRequest pr = createPullRequest();
            pr.setRepository(null);
            when(workspaceResolver.resolveForRepository(null)).thenReturn(Optional.empty());

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

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

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("practices disabled for workspace");
            verifyNoInteractions(practiceDetectionReadiness, practiceRepository, userRoleChecker);
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

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("auto-trigger disabled for workspace");
            verifyNoInteractions(practiceDetectionReadiness, practiceRepository, userRoleChecker);
        }

        @Test
        void skipWhenManualTriggerDisabled() {
            PullRequest pr = createPullRequest();
            Workspace workspace = createWorkspace();
            workspace.getFeatures().setPracticeReviewManualTriggerEnabled(false);
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.of(workspace));

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.MANUAL);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("manual trigger disabled for workspace");
            verifyNoInteractions(practiceDetectionReadiness, practiceRepository, userRoleChecker);
        }

        @Test
        void skipWhenBothTriggersDisabled() {
            PullRequest pr = createPullRequest();
            Workspace workspace = createWorkspace();
            workspace.getFeatures().setPracticeReviewAutoTriggerEnabled(false);
            workspace.getFeatures().setPracticeReviewManualTriggerEnabled(false);
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.of(workspace));

            GateDecision autoDecision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);
            GateDecision manualDecision = gate.evaluate(pr, SIGNAL, TriggerMode.MANUAL);

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
            when(practiceDetectionReadiness.hasRunnableAgent(WORKSPACE_ID)).thenReturn(false);

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.MANUAL);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no runnable practice-review agent");
        }

        @Test
        void continueWhenManualTriggerDisabledButModeIsAuto() {
            PullRequest pr = createPullRequest();
            Workspace workspace = createWorkspace();
            workspace.getFeatures().setPracticeReviewManualTriggerEnabled(false);
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.of(workspace));
            when(practiceDetectionReadiness.hasRunnableAgent(WORKSPACE_ID)).thenReturn(false);

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no runnable practice-review agent");
        }
    }

    @Nested
    class AgentBindingGateTests {

        @Test
        void skipWhenNoRunnablePracticeDetectionBinding() {
            PullRequest pr = createPullRequest();
            Workspace workspace = createWorkspace();
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.of(workspace));
            when(practiceDetectionReadiness.hasRunnableAgent(WORKSPACE_ID)).thenReturn(false);

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no runnable practice-review agent");
        }
    }

    @Nested
    class PracticeMatchingTests {

        @Test
        void skipWhenNoMatchingPractices() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(ScmSignals.PULL_REQUEST_REVIEWED);
            setupThroughPracticeMatching(pr, practice);

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no matching practices");
        }

        @Test
        void returnsAllMatchingPractices() {
            PullRequest pr = createPullRequest();
            Practice matching1 = createPractice(SIGNAL, ScmSignals.PULL_REQUEST_REVIEWED);
            Practice matching2 = createPractice(SIGNAL);
            Practice nonMatching = createPractice(ScmSignals.PULL_REQUEST_REVIEWED);
            Workspace workspace = createWorkspace();
            when(workspaceResolver.resolveForRepository("ls1intum/Hephaestus")).thenReturn(Optional.of(workspace));
            when(practiceDetectionReadiness.hasRunnableAgent(WORKSPACE_ID)).thenReturn(true);
            when(practiceRepository.findByWorkspaceIdAndUsedInNewReviewsTrue(WORKSPACE_ID)).thenReturn(
                List.of(matching1, matching2, nonMatching)
            );

            User assignee = createUser("test-user");
            pr.setAssignees(Set.of(assignee));
            when(userRoleChecker.isHealthy()).thenReturn(true);
            when(userRoleChecker.hasRole(TEST_PROVIDER_ID, subjectOf("test-user"), PRACTICE_REVIEW_ROLE)).thenReturn(
                true
            );

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

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
            PracticeReviewProperties runForAllProps = new PracticeReviewProperties(true, true, false, 15, false, false);
            PracticeReviewDetectionGate runForAllGate = new PracticeReviewDetectionGate(
                runForAllProps,
                userRoleChecker,
                practiceDetectionReadiness,
                practiceRepository,
                workspaceResolver
            );

            PullRequest pr = createPullRequest();
            Practice practice = createPractice(SIGNAL);
            setupThroughPracticeMatching(pr, practice);

            GateDecision decision = runForAllGate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

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
            Practice practice = createPractice(SIGNAL);
            setupThroughPracticeMatching(pr, practice);

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no assignee");
        }

        @Test
        void skipWhenNullAssignees() {
            PullRequest pr = createPullRequest();
            pr.setAssignees(null);
            Practice practice = createPractice(SIGNAL);
            setupThroughPracticeMatching(pr, practice);

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo("no assignee");
        }
    }

    @Nested
    class RoleCheckerHealthTests {

        @Test
        void skipWhenRoleCheckerUnhealthy() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(SIGNAL);
            setupThroughPracticeMatching(pr, practice);

            User assignee = createUser("test-user");
            pr.setAssignees(Set.of(assignee));

            when(userRoleChecker.isHealthy()).thenReturn(false);

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

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
            Practice practice = createPractice(SIGNAL);
            Workspace workspace = setupThroughPracticeMatching(pr, practice);

            User assignee = createUser("test-user");
            pr.setAssignees(Set.of(assignee));
            when(userRoleChecker.isHealthy()).thenReturn(true);
            when(userRoleChecker.hasRole(TEST_PROVIDER_ID, subjectOf("test-user"), PRACTICE_REVIEW_ROLE)).thenReturn(
                true
            );

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            GateDecision.Detect detect = (GateDecision.Detect) decision;
            assertThat(detect.workspace().getId()).isEqualTo(WORKSPACE_ID);
            assertThat(detect.matchedPractices()).containsExactly(practice);
        }

        @Test
        void skipWhenMissingRole() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(SIGNAL);
            setupThroughPracticeMatching(pr, practice);

            User assignee = createUser("test-user");
            pr.setAssignees(Set.of(assignee));
            when(userRoleChecker.isHealthy()).thenReturn(true);
            when(userRoleChecker.hasRole(TEST_PROVIDER_ID, subjectOf("test-user"), PRACTICE_REVIEW_ROLE)).thenReturn(
                false
            );

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo(
                "no assignee with role: " + PRACTICE_REVIEW_ROLE
            );
        }

        @Test
        void detectWhenAnyAssigneeHasRole() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(SIGNAL);
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

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            GateDecision.Detect detect = (GateDecision.Detect) decision;
            assertThat(detect.workspace().getId()).isEqualTo(WORKSPACE_ID);
            assertThat(detect.matchedPractices()).containsExactly(practice);
        }

        @Test
        void skipWhenNoAssigneeHasRole() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(SIGNAL);
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

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo(
                "no assignee with role: " + PRACTICE_REVIEW_ROLE
            );
        }

        @Test
        void skipWhenAssigneeHalfSyncedNoProvider() {
            // Fail-safe guard: an assignee whose provider didn't sync has no resolvable identity, so the
            // gate must skip it WITHOUT a role lookup (a null subject would otherwise be passed to hasRole).
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(SIGNAL);
            setupThroughPracticeMatching(pr, practice);

            User assignee = createUser("half-synced");
            assignee.setProvider(null);
            pr.setAssignees(Set.of(assignee));
            when(userRoleChecker.isHealthy()).thenReturn(true);

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo(
                "no assignee with role: " + PRACTICE_REVIEW_ROLE
            );
            verify(userRoleChecker, never()).hasRole(anyLong(), anyString(), anyString());
        }

        @Test
        void skipWhenAssigneeHalfSyncedNoNativeId() {
            // Same fail-safe guard for the other half of the (provider, subject) identity: a missing
            // native id also short-circuits before any role lookup.
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(SIGNAL);
            setupThroughPracticeMatching(pr, practice);

            User assignee = createUser("half-synced");
            assignee.setNativeId(null);
            pr.setAssignees(Set.of(assignee));
            when(userRoleChecker.isHealthy()).thenReturn(true);

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Skip.class);
            assertThat(((GateDecision.Skip) decision).reason()).isEqualTo(
                "no assignee with role: " + PRACTICE_REVIEW_ROLE
            );
            verify(userRoleChecker, never()).hasRole(anyLong(), anyString(), anyString());
        }

        @Test
        void skipWhenRoleCheckThrowsException() {
            PullRequest pr = createPullRequest();
            Practice practice = createPractice(SIGNAL);
            setupThroughPracticeMatching(pr, practice);

            User assignee = createUser("test-user");
            pr.setAssignees(Set.of(assignee));
            when(userRoleChecker.isHealthy()).thenReturn(true);
            when(userRoleChecker.hasRole(TEST_PROVIDER_ID, subjectOf("test-user"), PRACTICE_REVIEW_ROLE)).thenThrow(
                new RuntimeException("Connection refused")
            );

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

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
            Practice practice = createPractice(SIGNAL);
            Workspace workspace = setupThroughPracticeMatching(pr, practice);

            User assignee = createUser("test-user");
            pr.setAssignees(Set.of(assignee));
            when(userRoleChecker.isHealthy()).thenReturn(true);
            when(userRoleChecker.hasRole(TEST_PROVIDER_ID, subjectOf("test-user"), PRACTICE_REVIEW_ROLE)).thenReturn(
                true
            );

            GateDecision decision = gate.evaluate(pr, SIGNAL, TriggerMode.AUTO);

            assertThat(decision).isInstanceOf(GateDecision.Detect.class);
            GateDecision.Detect detect = (GateDecision.Detect) decision;
            assertThat(detect.workspace().getId()).isEqualTo(WORKSPACE_ID);
            assertThat(detect.workspace().getWorkspaceSlug()).isEqualTo("test-workspace");
            assertThat(detect.matchedPractices()).containsExactly(practice);
        }

        @Test
        void detectThrowsOnNullWorkspace() {
            Practice practice = createPractice(SIGNAL);

            Assertions.assertThrows(NullPointerException.class, () -> new GateDecision.Detect(null, List.of(practice)));
        }

        @Test
        void detectThrowsOnNullMatchedPractices() {
            Workspace workspace = createWorkspace();

            Assertions.assertThrows(NullPointerException.class, () -> new GateDecision.Detect(workspace, null));
        }

        @Test
        void detectMatchedPracticesIsUnmodifiable() {
            Practice practice = createPractice(SIGNAL);
            Workspace workspace = createWorkspace();

            GateDecision.Detect detect = new GateDecision.Detect(workspace, List.of(practice));

            Assertions.assertThrows(UnsupportedOperationException.class, () ->
                detect.matchedPractices().add(new Practice())
            );
        }
    }
}
