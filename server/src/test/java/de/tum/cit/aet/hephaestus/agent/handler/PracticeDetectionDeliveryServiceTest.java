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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedObservation;
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
import java.util.Map;
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
    private ReviewerResolver reviewerResolver;

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
            reviewerResolver,
            eventPublisher,
            objectMapper
        );

        Workspace workspace = new Workspace();
        ReflectionTestUtils.setField(workspace, "id", 1L);

        testPractice = new Practice();
        ReflectionTestUtils.setField(testPractice, "id", 10L);
        testPractice.setSlug("pr-description-quality");

        testJob = new AgentJob();
        ReflectionTestUtils.setField(testJob, "id", UUID.randomUUID());
        testJob.setWorkspace(workspace);
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("pull_request_id", 456L);
        testJob.setMetadata(metadata);

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
                    any(), // severity — null for non-BAD findings (ADR 0022), so any() not anyString()
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
     * NOT_APPLICABLE→null. The assessment slot sits right after presence on {@link ValidatedObservation}.
     */
    private ValidatedObservation validFinding(String slug, Presence presence) {
        Assessment assessment = switch (presence) {
            case PRESENT -> Assessment.GOOD;
            case ABSENT -> Assessment.BAD;
            case NOT_APPLICABLE -> null;
        };
        return new ValidatedObservation(
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

            ArgumentCaptor<String> fingerprintCaptor = ArgumentCaptor.forClass(String.class);
            verify(observationRepository).insertIfAbsent(
                any(UUID.class),
                eq("pr-description-quality:0:PULL_REQUEST:456:" + testJob.getId()),
                eq(testJob.getId()),
                eq(10L),
                isNull(), // practiceRevisionId — no revision in the mocked repo
                eq("PULL_REQUEST"),
                eq(456L),
                eq(789L), // aboutUserId
                eq("Test finding"),
                eq("PRESENT"), // presence (ADR 0022)
                eq("GOOD"), // assessment (former-GOOD practice, PRESENT → a strength)
                isNull(), // severity — coerced to null for a non-BAD finding (ADR 0022 invariant)
                eq(0.9f),
                isNull(),
                isNull(),
                fingerprintCaptor.capture(), // findingFingerprint == persisted recurrence_key
                any()
            );

            // The recurrence_key written to the row MUST equal the fingerprint the result map returns —
            // they are the single supersession identity, so any drift between them silently breaks re-review.
            assertThat(fingerprintCaptor.getValue())
                .as("persisted recurrence_key matches the returned findingFingerprint")
                .matches("[0-9a-f]{64}")
                .isEqualTo(result.findingFingerprints().values().iterator().next());

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
            var findings = new java.util.ArrayList<ValidatedObservation>();
            for (int i = 0; i < 7; i++) {
                findings.add(validFinding("pr-description-quality", Presence.ABSENT));
            }

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(7);
            assertThat(result.discardedDuplicate()).isZero();
        }

        @Test
        void persistsManyPositiveFindings() {
            var findings = new java.util.ArrayList<ValidatedObservation>();
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

            var findings = new java.util.ArrayList<ValidatedObservation>();
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
            var findings = new java.util.ArrayList<ValidatedObservation>();
            for (int i = 0; i < 10; i++) {
                findings.add(validFinding("pr-description-quality", Presence.NOT_APPLICABLE));
            }

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(10);
        }
    }

    @Nested
    class SeverityCoherence {

        /** Captures the severity (position 12) the native insert receives for one delivered finding. */
        private String capturedSeverityFor(ValidatedObservation finding) {
            service.deliver(testJob, List.of(finding));
            ArgumentCaptor<String> severityCaptor = ArgumentCaptor.forClass(String.class);
            verify(observationRepository).insertIfAbsent(
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
                any(), // assessment (null for NOT_APPLICABLE)
                severityCaptor.capture(),
                anyFloat(),
                any(),
                any(),
                anyString(),
                any()
            );
            return severityCaptor.getValue();
        }

        @Test
        @DisplayName("a BAD finding keeps its severity")
        void badFindingKeepsSeverity() {
            // ABSENT → BAD with Severity.INFO from the fixture helper.
            assertThat(capturedSeverityFor(validFinding("pr-description-quality", Presence.ABSENT))).isEqualTo("INFO");
        }

        @Test
        @DisplayName("a GOOD finding's severity is coerced to null (ADR 0022: severity is BAD-only)")
        void goodFindingSeverityCoercedToNull() {
            // PRESENT → GOOD, yet the fixture helper still carries Severity.INFO; it must not be persisted.
            assertThat(capturedSeverityFor(validFinding("pr-description-quality", Presence.PRESENT))).isNull();
        }

        @Test
        @DisplayName("a NOT_APPLICABLE finding's severity is coerced to null")
        void notApplicableFindingSeverityCoercedToNull() {
            assertThat(capturedSeverityFor(validFinding("pr-description-quality", Presence.NOT_APPLICABLE))).isNull();
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
                    any(), // severity — null for non-BAD findings (ADR 0022), so any() not anyString()
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
                isNull(), // severity — coerced to null for the PRESENT/GOOD finding (ADR 0022)
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
            when(issueRepository.findByIdWithAuthor(999L)).thenReturn(Optional.of(issue));

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
                eq(789L), // aboutUserId
                anyString(), // title
                eq("ABSENT"), // presence (ADR 0022)
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

    /**
     * Per-finding subject attribution for the reviewer-audience practices: the finding is filed against the
     * resolved REVIEWER, never the PR author. When the reviewer cannot be resolved, the finding is DISCARDED
     * (correctness rule #1: never misattribute a reviewer's craft to the author).
     */
    @Nested
    class ReviewerAudienceAttribution {

        private static final String REVIEWER_SLUG = "leaves-useful-specific-review-comments";

        private User reviewer(long id, String login) {
            User u = new User();
            ReflectionTestUtils.setField(u, "id", id);
            u.setLogin(login);
            return u;
        }

        private Practice reviewerPractice() {
            Practice p = new Practice();
            ReflectionTestUtils.setField(p, "id", 20L);
            p.setSlug(REVIEWER_SLUG);
            return p;
        }

        private ValidatedObservation reviewerFinding(String subjectLogin) {
            return new ValidatedObservation(
                REVIEWER_SLUG,
                "Reviewer finding",
                Presence.ABSENT,
                Assessment.BAD,
                Severity.MINOR,
                0.9f,
                null,
                null,
                null,
                List.of()
            ).withSubjectLogin(subjectLogin);
        }

        /** Captures the {@code about_user_id} (8th arg) persisted for the single inserted row. */
        private Long capturedAboutUserId() {
            ArgumentCaptor<Long> about = ArgumentCaptor.forClass(Long.class);
            verify(observationRepository).insertIfAbsent(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                about.capture(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
            return about.getValue();
        }

        @Test
        void singleReviewerAttributesEvenWithNullSubjectLogin() {
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(1L)).thenReturn(
                List.of(testPractice, reviewerPractice())
            );
            when(reviewerResolver.reviewersByLogin(anyLong(), any())).thenReturn(
                Map.of("reviewer-bob", reviewer(500L, "reviewer-bob"))
            );

            var result = service.deliver(testJob, List.of(reviewerFinding(null)));

            assertThat(result.inserted()).isEqualTo(1);
            // Deterministic fast path: exactly one reviewer, so no model trust needed — attributed to the reviewer.
            assertThat(capturedAboutUserId()).isEqualTo(500L);
        }

        @Test
        void multiReviewerAttributesToTheProposedReviewer() {
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(1L)).thenReturn(
                List.of(testPractice, reviewerPractice())
            );
            when(reviewerResolver.reviewersByLogin(anyLong(), any())).thenReturn(
                Map.of(
                    "reviewer-bob",
                    reviewer(500L, "reviewer-bob"),
                    "reviewer-carol",
                    reviewer(600L, "reviewer-carol")
                )
            );

            // Mixed-case subjectLogin normalizes to the "reviewer-carol" key.
            var result = service.deliver(testJob, List.of(reviewerFinding("Reviewer-Carol")));

            assertThat(result.inserted()).isEqualTo(1);
            assertThat(capturedAboutUserId()).isEqualTo(600L);
        }

        @Test
        void multiReviewerWithUnresolvedSubjectLoginIsDiscardedNotAttributedToAuthor() {
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(1L)).thenReturn(
                List.of(testPractice, reviewerPractice())
            );
            when(reviewerResolver.reviewersByLogin(anyLong(), any())).thenReturn(
                Map.of(
                    "reviewer-bob",
                    reviewer(500L, "reviewer-bob"),
                    "reviewer-carol",
                    reviewer(600L, "reviewer-carol")
                )
            );

            // subjectLogin names nobody in the reviewer set, and there are 2 reviewers → cannot attribute.
            var result = service.deliver(testJob, List.of(reviewerFinding("someone-else")));

            assertThat(result.inserted()).isZero();
            // NEVER persisted — and specifically never attributed to the author (789L).
            verify(observationRepository, never()).insertIfAbsent(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
            // The completion event still fires, author-scoped, counting the discard.
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().developerId()).isEqualTo(789L);
            assertThat(eventCaptor.getValue().findingsDiscarded()).isEqualTo(1);
        }

        @Test
        void authorAudienceGoesToAuthorAndNeverConsultsReviewerResolver() {
            var result = service.deliver(testJob, List.of(validFinding("pr-description-quality", Presence.PRESENT)));

            assertThat(result.inserted()).isEqualTo(1);
            assertThat(capturedAboutUserId()).isEqualTo(789L); // the PR author
            verifyNoInteractions(reviewerResolver); // reviewer set is resolved only when a reviewer finding exists
        }
    }
}
