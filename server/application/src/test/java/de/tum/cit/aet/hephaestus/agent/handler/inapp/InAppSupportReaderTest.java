package de.tum.cit.aet.hephaestus.agent.handler.inapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeFeedbackDeliveryPolicy;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyStage;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicySurface;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.PreviousInAppFeedback;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationVisibilityPolicy;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaults;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.PageRequest;

class InAppSupportReaderTest extends BaseUnitTest {

    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");
    private static final long WORKSPACE = 3L;
    private static final long RECIPIENT = 7L;
    private static final ArtifactKind PULL_REQUEST = ArtifactKind.of("scm.pull_request");

    @Mock
    private ObservationRepository observations;

    @Mock
    private ObservationVisibilityPolicy visibility;

    @Mock
    private PreviousInAppFeedback previous;

    @Mock
    private PracticeFeedbackDeliveryPolicy policy;

    @Mock
    private FeedbackRepository feedback;

    @Mock
    private WorkspaceReviewDefaultsProvider defaults;

    private InAppSupportReader reader;
    private AgentJob job;
    private Practice practice;

    @BeforeEach
    void setUp() {
        reader = new InAppSupportReader(
                observations, visibility, previous, policy, feedback, defaults, Clock.fixed(NOW, ZoneOffset.UTC));
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE);
        job = new AgentJob();
        job.setId(UUID.randomUUID());
        job.setWorkspace(workspace);
        practice = new Practice();
        practice.setSlug("handles-errors");
    }

    @Test
    void shouldSelectTheVisibleCitableOccurrencesForEachCurrentNegativePractice() {
        Observation onA = negative(11L, NOW.minus(Duration.ofDays(5)));
        Observation onB = negative(12L, NOW.minus(Duration.ofDays(2)));
        Observation hidden = negative(13L, NOW.minus(Duration.ofDays(1)));
        Observation current = negative(14L, NOW);
        allowRecipient();
        when(previous.find(WORKSPACE, RECIPIENT, "handles-errors", NOW)).thenReturn(Optional.empty());
        when(observations.findRecentForSubjectAndPractice(
                        WORKSPACE,
                        RECIPIENT,
                        "handles-errors",
                        NOW.minus(Duration.ofDays(InAppFeedbackRouter.PATTERN_WINDOW_DAYS)),
                        PageRequest.of(0, 50)))
                .thenReturn(List.of(current, hidden, onB, onA));
        when(visibility.permitsForNewDelivery(
                        eq(WORKSPACE), anyList(), eq(SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY)))
                .thenReturn(Set.of(current.getId(), onB.getId(), onA.getId()));
        autonomy(PracticeAutonomy.AUTOMATIC);

        InAppSupportContext support = reader.snapshot(job, List.of(current));

        assertThat(support.state()).isEqualTo(InAppSupportContext.State.COMPLETE);
        assertThat(support.readAt()).isEqualTo(NOW.toString());
        assertThat(support.practices()).singleElement().satisfies(selected -> {
            assertThat(selected.practiceSlug()).isEqualTo("handles-errors");
            assertThat(selected.decision()).isEqualTo(InAppRoutingDecision.ADMIT);
            assertThat(selected.occurrences())
                    .extracting(InAppSupportContext.Occurrence::observationId)
                    .containsExactlyInAnyOrder(current.getId(), onB.getId(), onA.getId());
            assertThat(selected.occurrences())
                    .extracting(InAppSupportContext.Occurrence::artifactId)
                    .containsExactlyInAnyOrder(14L, 12L, 11L);
        });
    }

    @Test
    void shouldSelectSupportThroughTheReaderAWorkerOnlyContextRegisters() {
        Observation current = negative(14L, NOW);
        Observation earlier = negative(12L, NOW.minus(Duration.ofDays(2)));
        allowRecipient();
        when(previous.find(WORKSPACE, RECIPIENT, "handles-errors", NOW)).thenReturn(Optional.empty());
        when(observations.findRecentForSubjectAndPractice(
                        eq(WORKSPACE), eq(RECIPIENT), eq("handles-errors"), any(), any()))
                .thenReturn(List.of(current, earlier));
        when(visibility.permitsForNewDelivery(
                        eq(WORKSPACE), anyList(), eq(SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY)))
                .thenReturn(Set.of(current.getId(), earlier.getId()));
        autonomy(PracticeAutonomy.AUTOMATIC);

        new ApplicationContextRunner()
                .withPropertyValues("hephaestus.runtime.server.enabled=false", "hephaestus.runtime.worker.enabled=true")
                .withBean(ObservationRepository.class, () -> observations)
                .withBean(ObservationVisibilityPolicy.class, () -> visibility)
                .withBean(PreviousInAppFeedback.class, () -> previous)
                .withBean(PracticeFeedbackDeliveryPolicy.class, () -> policy)
                .withBean(FeedbackRepository.class, () -> feedback)
                .withBean(WorkspaceReviewDefaultsProvider.class, () -> defaults)
                .withBean(Clock.class, () -> Clock.fixed(NOW, ZoneOffset.UTC))
                .withUserConfiguration(InAppSupportReader.class)
                .run(context -> {
                    InAppSupportReader registered =
                            context.getBeanProvider(InAppSupportReader.class).getIfAvailable();
                    assertThat(registered).isNotNull();
                    InAppSupportContext support = registered.snapshot(job, List.of(current));

                    assertThat(support.state()).isEqualTo(InAppSupportContext.State.COMPLETE);
                    assertThat(support.practices())
                            .singleElement()
                            .satisfies(selected -> assertThat(selected.occurrences())
                                    .extracting(InAppSupportContext.Occurrence::observationId)
                                    .containsExactlyInAnyOrder(current.getId(), earlier.getId()));
                });
    }

    /**
     * Composition is told what the page would refuse before it writes a card, and none of a refused practice's
     * support is copied into the sandbox. The page still decides again when it prepares.
     */
    @ParameterizedTest
    @EnumSource(
            value = InAppRoutingDecision.class,
            names = {"UNCORROBORATED", "PRACTICE_REQUIRES_APPROVAL", "RECENTLY_SURFACED"})
    void shouldRouteEachPracticeAsThePageWouldAndCopyNoSupportItRefuses(InAppRoutingDecision refusal) {
        Observation current = negative(14L, NOW);
        Observation earlier = negative(12L, NOW.minus(Duration.ofDays(2)));
        List<Observation> window =
                refusal == InAppRoutingDecision.UNCORROBORATED ? List.of(current) : List.of(current, earlier);
        allowRecipient();
        when(previous.find(WORKSPACE, RECIPIENT, "handles-errors", NOW)).thenReturn(Optional.empty());
        when(observations.findRecentForSubjectAndPractice(
                        eq(WORKSPACE), eq(RECIPIENT), eq("handles-errors"), any(), any()))
                .thenReturn(window);
        when(visibility.permitsForNewDelivery(
                        eq(WORKSPACE), anyList(), eq(SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY)))
                .thenReturn(window.stream().map(Observation::getId).collect(Collectors.toSet()));
        autonomy(
                refusal == InAppRoutingDecision.PRACTICE_REQUIRES_APPROVAL
                        ? PracticeAutonomy.OFF
                        : PracticeAutonomy.AUTOMATIC);
        if (refusal == InAppRoutingDecision.RECENTLY_SURFACED) {
            when(feedback.lastInAppSurfacedAt(WORKSPACE, RECIPIENT, "handles-errors"))
                    .thenReturn(Optional.of(NOW.minus(Duration.ofDays(1))));
        }

        InAppSupportContext support = reader.snapshot(job, List.of(current));

        assertThat(support.state()).isEqualTo(InAppSupportContext.State.COMPLETE);
        assertThat(support.practices()).singleElement().satisfies(selected -> {
            assertThat(selected.practiceSlug()).isEqualTo("handles-errors");
            assertThat(selected.decision()).isEqualTo(refusal);
            assertThat(selected.occurrences()).isEmpty();
        });
    }

    @Test
    void shouldReturnTheRecipientPolicysRefusalWithoutReadingSupport() {
        when(policy.allowsComposition(job, DeliveryPolicySurface.IN_APP)).thenReturn(true);
        when(policy.evaluateForRecipient(
                        job, DeliveryPolicyStage.COMPOSITION, null, DeliveryPolicySurface.IN_APP, RECIPIENT, List.of()))
                .thenReturn(new PracticeFeedbackDeliveryPolicy.DeliveryDecision(
                        false, FeedbackSuppressionReason.RECIPIENT_OPTED_OUT));

        InAppSupportContext support = reader.snapshot(job, List.of(negative(14L, NOW)));

        assertThat(support.state()).isEqualTo(InAppSupportContext.State.REFUSED);
        assertThat(support.refusal()).isEqualTo(FeedbackSuppressionReason.RECIPIENT_OPTED_OUT);
        assertThat(support.practices()).isEmpty();
        verifyNoInteractions(observations);
    }

    @Test
    void shouldNotAttributeSupportWhenTheAdmittedNegativesConcernSeveralPeople() {
        Observation other = Observation.builder()
                .id(UUID.randomUUID())
                .agentJobId(job.getId())
                .workspaceId(WORKSPACE)
                .practice(practice)
                .artifactKind(PULL_REQUEST)
                .artifactId(14L)
                .aboutUserId(RECIPIENT + 1)
                .outcome(Outcome.NOT_MET)
                .severity(Severity.MINOR)
                .origin(ObservationOrigin.LIVE)
                .summary("Caught and ignored")
                .observedAt(NOW)
                .build();

        InAppSupportContext support = reader.snapshot(job, List.of(negative(14L, NOW), other));

        assertThat(support.state()).isEqualTo(InAppSupportContext.State.UNAVAILABLE);
        verifyNoInteractions(policy, observations);
    }

    @Test
    void shouldAnswerCompleteAndEmptyWhenNoAdmittedObservationIsNegative() {
        Observation met = Observation.builder()
                .id(UUID.randomUUID())
                .agentJobId(job.getId())
                .workspaceId(WORKSPACE)
                .practice(practice)
                .artifactKind(PULL_REQUEST)
                .artifactId(14L)
                .aboutUserId(RECIPIENT)
                .outcome(Outcome.MET)
                .origin(ObservationOrigin.LIVE)
                .summary("Errors are reported")
                .observedAt(NOW)
                .build();

        InAppSupportContext support = reader.snapshot(job, List.of(met));

        assertThat(support.state()).isEqualTo(InAppSupportContext.State.COMPLETE);
        assertThat(support.practices()).isEmpty();
        verifyNoInteractions(policy, observations);
    }

    private void allowRecipient() {
        when(policy.allowsComposition(job, DeliveryPolicySurface.IN_APP)).thenReturn(true);
        when(policy.evaluateForRecipient(any(), any(), any(), any(), any(Long.class), anyList()))
                .thenReturn(new PracticeFeedbackDeliveryPolicy.DeliveryDecision(true, null));
    }

    private void autonomy(PracticeAutonomy tier) {
        when(defaults.forWorkspace(WORKSPACE)).thenReturn(new WorkspaceReviewDefaults(PracticeAutonomy.AUTOMATIC));
        ObservationRepository.ObservationPracticeAutonomy row =
                mock(ObservationRepository.ObservationPracticeAutonomy.class);
        when(row.getPracticeAutonomy()).thenReturn(tier);
        when(observations.findPracticeAutonomyFor(anyList(), eq(WORKSPACE))).thenReturn(List.of(row));
    }

    private Observation negative(long workId, Instant observedAt) {
        return Observation.builder()
                .id(UUID.randomUUID())
                .agentJobId(UUID.randomUUID())
                .workspaceId(WORKSPACE)
                .practice(practice)
                .artifactKind(PULL_REQUEST)
                .artifactId(workId)
                .aboutUserId(RECIPIENT)
                .outcome(Outcome.NOT_MET)
                .severity(Severity.MINOR)
                .origin(ObservationOrigin.LIVE)
                .summary("Caught and ignored")
                .observedAt(observedAt)
                .build();
    }
}
