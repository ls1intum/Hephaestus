package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.PracticeDetectionCompletedEvent;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class PracticeDetectionDeliveryServiceTest extends BaseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository practiceRevisionRepository;

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository issueRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<PracticeDetectionCompletedEvent> eventCaptor;

    private PracticeDetectionDeliveryService service;

    private Practice testPractice;
    private AgentJob testJob;
    private PullRequest testPr;
    private User testAuthor;

    @BeforeEach
    void setUp() {
        service = new PracticeDetectionDeliveryService(
            practiceRepository,
            practiceRevisionRepository,
            observationRepository,
            pullRequestRepository,
            issueRepository,
            eventPublisher,
            objectMapper
        );

        // Create test workspace
        Workspace workspace = new Workspace();
        ReflectionTestUtils.setField(workspace, "id", 1L);

        // Create test practice
        testPractice = new Practice();
        ReflectionTestUtils.setField(testPractice, "id", 10L);
        testPractice.setSlug("pr-description-quality");

        // Create test job
        testJob = new AgentJob();
        ReflectionTestUtils.setField(testJob, "id", UUID.randomUUID());
        testJob.setWorkspace(workspace);
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("pull_request_id", 456L);
        testJob.setMetadata(metadata);

        // Create test PR with author
        testAuthor = new User();
        ReflectionTestUtils.setField(testAuthor, "id", 789L);
        testAuthor.setLogin("developer");
        testPr = new PullRequest();
        testPr.setAuthor(testAuthor);

        // Default stubs (lenient because not all tests exercise all code paths)
        lenient().when(practiceRepository.findByWorkspaceIdAndActiveTrue(1L)).thenReturn(List.of(testPractice));
        lenient().when(pullRequestRepository.findByIdWithAuthor(456L)).thenReturn(Optional.of(testPr));
        lenient()
            .when(
                observationRepository.insertIfAbsent(
                    any(),
                    anyString(),
                    any(),
                    anyLong(),
                    any(), // practiceRevisionId
                    anyString(),
                    anyLong(),
                    anyLong(),
                    any(),
                    anyString(),
                    any(), // assessment — null for NOT_APPLICABLE, so any() (anyString() would not match null)
                    anyString(),
                    anyFloat(),
                    any(),
                    any(),
                    anyString(),
                    any()
                )
            )
            .thenReturn(1);
    }

    /**
     * Build a finding whose valence follows the former-GOOD practice convention used across these
     * fixtures (pr-description-quality, error-handling): PRESENT→GOOD strength, ABSENT→BAD gap,
     * NOT_APPLICABLE→null. The assessment slot sits right after presence on {@link ValidatedFinding}.
     */
    private ValidatedFinding validFinding(String slug, Presence presence) {
        Assessment assessment = switch (presence) {
            case PRESENT -> Assessment.GOOD;
            case ABSENT -> Assessment.BAD;
            case NOT_APPLICABLE -> null;
        };
        return new ValidatedFinding(
            slug,
            "Test finding",
            presence,
            assessment,
            Severity.INFO,
            0.9f,
            null,
            null,
            null,
            List.of()
        );
    }

    @Nested
    class HappyPath {

        @Test
        void persistsValidFinding() {
            var findings = List.of(validFinding("pr-description-quality", Presence.PRESENT));

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(1);
            assertThat(result.discardedUnknownSlug()).isZero();
            assertThat(result.discardedDuplicate()).isZero();

            verify(observationRepository).insertIfAbsent(
                any(UUID.class),
                eq("pr-description-quality:0:PULL_REQUEST:456:" + testJob.getId()),
                eq(testJob.getId()),
                eq(10L),
                isNull(), // practiceRevisionId — no revision in the mocked repo
                eq("PULL_REQUEST"),
                eq(456L),
                eq(789L), // subjectUserId
                eq("Test finding"),
                eq("PRESENT"), // presence (OBSERVED → PRESENT, ADR 0022)
                eq("GOOD"), // assessment (former-GOOD practice OBSERVED → strength)
                eq("INFO"),
                eq(0.9f),
                isNull(),
                isNull(),
                anyString(),
                any()
            );

            verify(eventPublisher).publishEvent(eventCaptor.capture());
            PracticeDetectionCompletedEvent event = eventCaptor.getValue();
            assertThat(event.agentJobId()).isEqualTo(testJob.getId());
            assertThat(event.workspaceId()).isEqualTo(1L);
            assertThat(event.findingsInserted()).isEqualTo(1);
            assertThat(event.findingsDiscarded()).isZero();
            assertThat(event.hasNegative()).isFalse();
        }
    }

    @Nested
    class PracticeResolution {

        @Test
        void unknownSlug() {
            var findings = List.of(validFinding("unknown-practice", Presence.PRESENT));

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isZero();
            assertThat(result.discardedUnknownSlug()).isEqualTo(1);
            verify(observationRepository, never()).insertIfAbsent(
                any(),
                anyString(),
                any(),
                anyLong(),
                any(), // practiceRevisionId
                anyString(),
                anyLong(),
                anyLong(),
                any(),
                anyString(),
                anyString(),
                anyString(),
                anyFloat(),
                any(),
                any(),
                anyString(),
                any()
            );
        }

        @Test
        @DisplayName("returns 0 inserted when workspace has no practices")
        void emptyPracticeCatalog() {
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(1L)).thenReturn(List.of());
            var findings = List.of(validFinding("pr-description-quality", Presence.PRESENT));

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isZero();
            assertThat(result.discardedUnknownSlug()).isEqualTo(1);
        }
    }

    @Nested
    class TargetResolution {

        @Test
        @DisplayName("throws when pull request not found")
        void prNotFound() {
            when(pullRequestRepository.findByIdWithAuthor(456L)).thenReturn(Optional.empty());
            var findings = List.of(validFinding("pr-description-quality", Presence.PRESENT));

            assertThatThrownBy(() -> service.deliver(testJob, findings))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("Pull request not found");
        }

        @Test
        @DisplayName("throws when pull request has no author")
        void prNoAuthor() {
            testPr.setAuthor(null);
            var findings = List.of(validFinding("pr-description-quality", Presence.PRESENT));

            assertThatThrownBy(() -> service.deliver(testJob, findings))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("no author");
        }
    }

    @Nested
    class MetadataValidation {

        @Test
        void nullMetadata() {
            testJob.setMetadata(null);
            var findings = List.of(validFinding("pr-description-quality", Presence.PRESENT));

            assertThatThrownBy(() -> service.deliver(testJob, findings))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("Missing job metadata");
        }

        @Test
        void missingPullRequestId() {
            testJob.setMetadata(objectMapper.createObjectNode());
            var findings = List.of(validFinding("pr-description-quality", Presence.PRESENT));

            assertThatThrownBy(() -> service.deliver(testJob, findings))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("Missing pull_request_id");
        }
    }

    @Nested
    class MultipleNegatives {

        @Test
        void persistsAllNegativesForPractice() {
            var findings = new java.util.ArrayList<ValidatedFinding>();
            for (int i = 0; i < 7; i++) {
                findings.add(validFinding("pr-description-quality", Presence.ABSENT));
            }

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(7);
            assertThat(result.discardedDuplicate()).isZero();
        }

        @Test
        void persistsManyPositiveFindings() {
            var findings = new java.util.ArrayList<ValidatedFinding>();
            for (int i = 0; i < 10; i++) {
                findings.add(validFinding("pr-description-quality", Presence.PRESENT));
            }

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(10);
            assertThat(result.discardedDuplicate()).isZero();
        }

        @Test
        void persistsNegativesIndependentlyPerPractice() {
            Practice otherPractice = new Practice();
            ReflectionTestUtils.setField(otherPractice, "id", 20L);
            otherPractice.setSlug("error-handling");
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(1L)).thenReturn(
                List.of(testPractice, otherPractice)
            );

            var findings = new java.util.ArrayList<ValidatedFinding>();
            for (int i = 0; i < 5; i++) {
                findings.add(validFinding("pr-description-quality", Presence.ABSENT));
                findings.add(validFinding("error-handling", Presence.ABSENT));
            }

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(10);
        }
    }

    @Nested
    class NotApplicableObservation {

        @Test
        @DisplayName("persists NOT_APPLICABLE finding without counting as negative")
        void notApplicablePersisted() {
            var findings = List.of(validFinding("pr-description-quality", Presence.NOT_APPLICABLE));

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(1);
            assertThat(result.hasNegative()).isFalse();
        }

        @Test
        void persistsManyNotApplicableFindings() {
            var findings = new java.util.ArrayList<ValidatedFinding>();
            for (int i = 0; i < 10; i++) {
                findings.add(validFinding("pr-description-quality", Presence.NOT_APPLICABLE));
            }

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(10);
        }
    }

    @Nested
    class Idempotency {

        @Test
        void duplicateKey() {
            when(
                observationRepository.insertIfAbsent(
                    any(),
                    anyString(),
                    any(),
                    anyLong(),
                    any(), // practiceRevisionId
                    anyString(),
                    anyLong(),
                    anyLong(),
                    any(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyFloat(),
                    any(),
                    any(),
                    anyString(),
                    any()
                )
            ).thenReturn(0);

            var findings = List.of(validFinding("pr-description-quality", Presence.PRESENT));

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isZero();
            assertThat(result.discardedDuplicate()).isEqualTo(1);
        }

        @Test
        void keyFormat() {
            var findings = List.of(validFinding("pr-description-quality", Presence.PRESENT));

            service.deliver(testJob, findings);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(observationRepository).insertIfAbsent(
                any(),
                keyCaptor.capture(),
                any(),
                anyLong(),
                any(), // practiceRevisionId
                anyString(),
                anyLong(),
                anyLong(),
                any(),
                anyString(),
                anyString(),
                anyString(),
                anyFloat(),
                any(),
                any(),
                anyString(),
                any()
            );

            String key = keyCaptor.getValue();
            assertThat(key).isEqualTo("pr-description-quality:0:PULL_REQUEST:456:" + testJob.getId());
        }
    }

    @Nested
    class EventPublication {

        @Test
        void correctCounts() {
            // One known slug, one unknown
            Practice otherPractice = new Practice();
            ReflectionTestUtils.setField(otherPractice, "id", 20L);
            otherPractice.setSlug("error-handling");
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(1L)).thenReturn(
                List.of(testPractice, otherPractice)
            );

            var findings = List.of(
                validFinding("pr-description-quality", Presence.PRESENT),
                validFinding("error-handling", Presence.ABSENT),
                validFinding("unknown-slug", Presence.PRESENT)
            );

            service.deliver(testJob, findings);

            verify(eventPublisher).publishEvent(eventCaptor.capture());
            PracticeDetectionCompletedEvent event = eventCaptor.getValue();
            assertThat(event.findingsInserted()).isEqualTo(2);
            assertThat(event.findingsDiscarded()).isEqualTo(1); // unknown slug
            assertThat(event.hasNegative()).isTrue(); // error-handling finding is NEGATIVE
            assertThat(event.developerId()).isEqualTo(789L);
            assertThat(event.artifactType()).isEqualTo(WorkArtifact.PULL_REQUEST);
            assertThat(event.artifactId()).isEqualTo(456L);
        }
    }

    @Nested
    class IssueRouting {

        @Test
        void routesToIssueTargetAndAuthorWhenArtifactTypeIsIssue() {
            // Job carries artifact_type=ISSUE + issue_id → resolve the Issue (TYPE-filtered) + its author.
            var issue = new de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue();
            ReflectionTestUtils.setField(issue, "id", 999L);
            issue.setAuthor(testAuthor);
            when(issueRepository.findByIdWithRepository(999L)).thenReturn(Optional.of(issue));

            ObjectNode meta = new ObjectMapper().createObjectNode();
            meta.put("artifact_type", "ISSUE");
            meta.put("issue_id", 999L);
            testJob.setMetadata(meta);

            var findings = List.of(validFinding("pr-description-quality", Presence.ABSENT));
            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(1);
            verify(observationRepository).insertIfAbsent(
                any(),
                eq("pr-description-quality:0:ISSUE:999:" + testJob.getId()),
                eq(testJob.getId()),
                anyLong(),
                isNull(), // practiceRevisionId — no revision in the mocked repo
                eq("ISSUE"),
                eq(999L),
                eq(789L), // subjectUserId
                anyString(), // title
                eq("ABSENT"), // presence (NOT_OBSERVED → ABSENT, ADR 0022)
                eq("BAD"), // assessment (former-GOOD practice ABSENT → gap)
                anyString(), // severity
                anyFloat(),
                any(),
                any(),
                anyString(),
                any()
            );
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().artifactType()).isEqualTo(WorkArtifact.ISSUE);
            assertThat(eventCaptor.getValue().artifactId()).isEqualTo(999L);
        }
    }
}
