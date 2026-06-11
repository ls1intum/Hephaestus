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
import de.tum.cit.aet.hephaestus.practices.finding.PracticeDetectionCompletedEvent;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.cit.aet.hephaestus.practices.model.FocusArtifact;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.Verdict;
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
    private PracticeFindingRepository practiceFindingRepository;

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
            practiceFindingRepository,
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
        testAuthor.setLogin("contributor");
        testPr = new PullRequest();
        testPr.setAuthor(testAuthor);

        // Default stubs (lenient because not all tests exercise all code paths)
        lenient().when(practiceRepository.findByWorkspaceIdAndActiveTrue(1L)).thenReturn(List.of(testPractice));
        lenient().when(pullRequestRepository.findByIdWithAuthor(456L)).thenReturn(Optional.of(testPr));
        lenient()
            .when(
                practiceFindingRepository.insertIfAbsent(
                    any(),
                    anyString(),
                    any(),
                    anyLong(),
                    anyString(),
                    anyLong(),
                    anyLong(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyFloat(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            )
            .thenReturn(1);
    }

    private ValidatedFinding validFinding(String slug, Verdict verdict) {
        return new ValidatedFinding(slug, "Test finding", verdict, Severity.INFO, 0.9f, null, null, null, List.of());
    }

    @Nested
    class HappyPath {

        @Test
        void persistsValidFinding() {
            var findings = List.of(validFinding("pr-description-quality", Verdict.POSITIVE));

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(1);
            assertThat(result.discardedUnknownSlug()).isZero();
            assertThat(result.discardedDuplicate()).isZero();

            verify(practiceFindingRepository).insertIfAbsent(
                any(UUID.class),
                eq("pr-description-quality:0:PULL_REQUEST:456:" + testJob.getId()),
                eq(testJob.getId()),
                eq(10L),
                eq("PULL_REQUEST"),
                eq(456L),
                eq(789L),
                eq("Test finding"),
                eq("POSITIVE"),
                eq("INFO"),
                eq(0.9f),
                isNull(),
                isNull(),
                isNull(),
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
            var findings = List.of(validFinding("unknown-practice", Verdict.POSITIVE));

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isZero();
            assertThat(result.discardedUnknownSlug()).isEqualTo(1);
            verify(practiceFindingRepository, never()).insertIfAbsent(
                any(),
                anyString(),
                any(),
                anyLong(),
                anyString(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyFloat(),
                any(),
                any(),
                any(),
                any()
            );
        }

        @Test
        @DisplayName("returns 0 inserted when workspace has no practices")
        void emptyPracticeCatalog() {
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(1L)).thenReturn(List.of());
            var findings = List.of(validFinding("pr-description-quality", Verdict.POSITIVE));

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
            var findings = List.of(validFinding("pr-description-quality", Verdict.POSITIVE));

            assertThatThrownBy(() -> service.deliver(testJob, findings))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("Pull request not found");
        }

        @Test
        @DisplayName("throws when pull request has no author")
        void prNoAuthor() {
            testPr.setAuthor(null);
            var findings = List.of(validFinding("pr-description-quality", Verdict.POSITIVE));

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
            var findings = List.of(validFinding("pr-description-quality", Verdict.POSITIVE));

            assertThatThrownBy(() -> service.deliver(testJob, findings))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("Missing job metadata");
        }

        @Test
        void missingPullRequestId() {
            testJob.setMetadata(objectMapper.createObjectNode());
            var findings = List.of(validFinding("pr-description-quality", Verdict.POSITIVE));

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
                findings.add(validFinding("pr-description-quality", Verdict.NEGATIVE));
            }

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(7);
            assertThat(result.discardedDuplicate()).isZero();
        }

        @Test
        void persistsManyPositiveFindings() {
            var findings = new java.util.ArrayList<ValidatedFinding>();
            for (int i = 0; i < 10; i++) {
                findings.add(validFinding("pr-description-quality", Verdict.POSITIVE));
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
                findings.add(validFinding("pr-description-quality", Verdict.NEGATIVE));
                findings.add(validFinding("error-handling", Verdict.NEGATIVE));
            }

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(10);
        }
    }

    @Nested
    class NotApplicableVerdict {

        @Test
        @DisplayName("persists NOT_APPLICABLE finding without counting as negative")
        void notApplicablePersisted() {
            var findings = List.of(validFinding("pr-description-quality", Verdict.NOT_APPLICABLE));

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(1);
            assertThat(result.hasNegative()).isFalse();
        }

        @Test
        void persistsManyNotApplicableFindings() {
            var findings = new java.util.ArrayList<ValidatedFinding>();
            for (int i = 0; i < 10; i++) {
                findings.add(validFinding("pr-description-quality", Verdict.NOT_APPLICABLE));
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
                practiceFindingRepository.insertIfAbsent(
                    any(),
                    anyString(),
                    any(),
                    anyLong(),
                    anyString(),
                    anyLong(),
                    anyLong(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyFloat(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            ).thenReturn(0);

            var findings = List.of(validFinding("pr-description-quality", Verdict.POSITIVE));

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isZero();
            assertThat(result.discardedDuplicate()).isEqualTo(1);
        }

        @Test
        void keyFormat() {
            var findings = List.of(validFinding("pr-description-quality", Verdict.POSITIVE));

            service.deliver(testJob, findings);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(practiceFindingRepository).insertIfAbsent(
                any(),
                keyCaptor.capture(),
                any(),
                anyLong(),
                anyString(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyFloat(),
                any(),
                any(),
                any(),
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
                validFinding("pr-description-quality", Verdict.POSITIVE),
                validFinding("error-handling", Verdict.NEGATIVE),
                validFinding("unknown-slug", Verdict.POSITIVE)
            );

            service.deliver(testJob, findings);

            verify(eventPublisher).publishEvent(eventCaptor.capture());
            PracticeDetectionCompletedEvent event = eventCaptor.getValue();
            assertThat(event.findingsInserted()).isEqualTo(2);
            assertThat(event.findingsDiscarded()).isEqualTo(1); // unknown slug
            assertThat(event.hasNegative()).isTrue(); // error-handling finding is NEGATIVE
            assertThat(event.contributorId()).isEqualTo(789L);
            assertThat(event.targetType()).isEqualTo(FocusArtifact.PULL_REQUEST);
            assertThat(event.targetId()).isEqualTo(456L);
        }
    }

    @Nested
    class IssueRouting {

        @Test
        void routesToIssueTargetAndAuthorWhenTargetTypeIsIssue() {
            // Job carries target_type=ISSUE + issue_id → resolve the Issue (TYPE-filtered) + its author.
            var issue = new de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue();
            ReflectionTestUtils.setField(issue, "id", 999L);
            issue.setAuthor(testAuthor);
            when(issueRepository.findByIdWithRepository(999L)).thenReturn(Optional.of(issue));

            ObjectNode meta = new ObjectMapper().createObjectNode();
            meta.put("target_type", "ISSUE");
            meta.put("issue_id", 999L);
            testJob.setMetadata(meta);

            var findings = List.of(validFinding("pr-description-quality", Verdict.NEGATIVE));
            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(1);
            verify(practiceFindingRepository).insertIfAbsent(
                any(),
                eq("pr-description-quality:0:ISSUE:999:" + testJob.getId()),
                eq(testJob.getId()),
                anyLong(),
                eq("ISSUE"),
                eq(999L),
                eq(789L),
                anyString(),
                eq("NEGATIVE"),
                anyString(),
                anyFloat(),
                any(),
                any(),
                any(),
                any()
            );
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().targetType()).isEqualTo(FocusArtifact.ISSUE);
            assertThat(eventCaptor.getValue().targetId()).isEqualTo(999L);
        }
    }
}
