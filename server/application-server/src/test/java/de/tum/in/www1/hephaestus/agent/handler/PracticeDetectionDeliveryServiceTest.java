package de.tum.in.www1.hephaestus.agent.handler;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.practices.finding.PracticeDetectionCompletedEvent;
import de.tum.in.www1.hephaestus.practices.finding.PracticeDetectionProperties;
import de.tum.in.www1.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.in.www1.hephaestus.practices.model.CaMethod;
import de.tum.in.www1.hephaestus.practices.model.Practice;
import de.tum.in.www1.hephaestus.practices.model.Severity;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import de.tum.in.www1.hephaestus.workspace.Workspace;
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

@DisplayName("PracticeDetectionDeliveryService")
class PracticeDetectionDeliveryServiceTest extends BaseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private PracticeFindingRepository practiceFindingRepository;

    @Mock
    private PullRequestRepository pullRequestRepository;

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
        var properties = new PracticeDetectionProperties(5, 100);
        service = new PracticeDetectionDeliveryService(
            practiceRepository,
            practiceFindingRepository,
            pullRequestRepository,
            properties,
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
                    any(),
                    any()
                )
            )
            .thenReturn(1);
    }

    private ValidatedFinding validFinding(String slug, Verdict verdict) {
        return new ValidatedFinding(slug, "Test finding", verdict, Severity.INFO, 0.9f, null, null, null, null);
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("persists valid finding and publishes event")
        void persistsValidFinding() {
            var findings = List.of(validFinding("pr-description-quality", Verdict.POSITIVE));

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(1);
            assertThat(result.discardedUnknownSlug()).isZero();
            assertThat(result.discardedOverCap()).isZero();
            assertThat(result.discardedDuplicate()).isZero();

            verify(practiceFindingRepository).insertIfAbsent(
                any(UUID.class),
                eq("pr-description-quality:pull_request:456:" + testJob.getId() + ":0"),
                eq(testJob.getId()),
                eq(10L),
                eq("pull_request"),
                eq(456L),
                eq(789L),
                eq("Test finding"),
                eq("POSITIVE"),
                eq("INFO"),
                eq(0.9f),
                isNull(),
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
    @DisplayName("Practice resolution")
    class PracticeResolution {

        @Test
        @DisplayName("discards finding for unknown practice slug")
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
    @DisplayName("Target resolution")
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
    @DisplayName("Metadata validation")
    class MetadataValidation {

        @Test
        @DisplayName("throws when metadata is null")
        void nullMetadata() {
            testJob.setMetadata(null);
            var findings = List.of(validFinding("pr-description-quality", Verdict.POSITIVE));

            assertThatThrownBy(() -> service.deliver(testJob, findings))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("Missing pull_request_id");
        }

        @Test
        @DisplayName("throws when pull_request_id is missing from metadata")
        void missingPullRequestId() {
            testJob.setMetadata(objectMapper.createObjectNode());
            var findings = List.of(validFinding("pr-description-quality", Verdict.POSITIVE));

            assertThatThrownBy(() -> service.deliver(testJob, findings))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("Missing pull_request_id");
        }
    }

    @Nested
    @DisplayName("Negative cap")
    class NegativeCap {

        @Test
        @DisplayName("allows up to cap negatives per practice")
        void upToCapAllowed() {
            var findings = new java.util.ArrayList<ValidatedFinding>();
            for (int i = 0; i < 5; i++) {
                findings.add(validFinding("pr-description-quality", Verdict.NEGATIVE));
            }

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(5);
            assertThat(result.discardedOverCap()).isZero();
        }

        @Test
        @DisplayName("discards negatives beyond cap")
        void beyondCapDiscarded() {
            var findings = new java.util.ArrayList<ValidatedFinding>();
            for (int i = 0; i < 7; i++) {
                findings.add(validFinding("pr-description-quality", Verdict.NEGATIVE));
            }

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(5);
            assertThat(result.discardedOverCap()).isEqualTo(2);
        }

        @Test
        @DisplayName("does not cap POSITIVE findings")
        void positiveNotCapped() {
            var findings = new java.util.ArrayList<ValidatedFinding>();
            for (int i = 0; i < 10; i++) {
                findings.add(validFinding("pr-description-quality", Verdict.POSITIVE));
            }

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(10);
            assertThat(result.discardedOverCap()).isZero();
        }

        @Test
        @DisplayName("caps negatives independently per practice")
        void independentCapsPerPractice() {
            Practice otherPractice = new Practice();
            ReflectionTestUtils.setField(otherPractice, "id", 20L);
            otherPractice.setSlug("error-handling");
            when(practiceRepository.findByWorkspaceIdAndActiveTrue(1L)).thenReturn(
                List.of(testPractice, otherPractice)
            );

            var findings = new java.util.ArrayList<ValidatedFinding>();
            // 5 negatives for each practice — both should be fully allowed
            for (int i = 0; i < 5; i++) {
                findings.add(validFinding("pr-description-quality", Verdict.NEGATIVE));
                findings.add(validFinding("error-handling", Verdict.NEGATIVE));
            }

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(10);
            assertThat(result.discardedOverCap()).isZero();
        }

        @Test
        @DisplayName("does not cap NOT_APPLICABLE findings")
        void notApplicableNotCapped() {
            var findings = new java.util.ArrayList<ValidatedFinding>();
            for (int i = 0; i < 10; i++) {
                findings.add(validFinding("pr-description-quality", Verdict.NOT_APPLICABLE));
            }

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("duplicate key returns 0 from insertIfAbsent")
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
        @DisplayName("idempotency key format is correct")
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
                any(),
                any()
            );

            String key = keyCaptor.getValue();
            assertThat(key).isEqualTo("pr-description-quality:pull_request:456:" + testJob.getId() + ":0");
        }
    }

    @Nested
    @DisplayName("Event publication")
    class EventPublication {

        @Test
        @DisplayName("event carries correct counts")
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
            assertThat(event.targetType()).isEqualTo("pull_request");
            assertThat(event.targetId()).isEqualTo(456L);
        }
    }
}
