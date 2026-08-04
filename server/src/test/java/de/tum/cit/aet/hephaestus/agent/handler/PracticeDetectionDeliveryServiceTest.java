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

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceUseAudience;
import de.tum.cit.aet.hephaestus.integration.core.fabric.ContentAddressedStore;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.PracticeDetectionCompletedEvent;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
    private de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository practiceRevisionRepository;

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository issueRepository;

    @Mock
    private SlackThreadRepository slackThreadRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ContentAddressedStore cas;

    @Mock
    private de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry sourceCatalogs;

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
            practiceRevisionRepository,
            observationRepository,
            pullRequestRepository,
            issueRepository,
            slackThreadRepository,
            eventPublisher,
            objectMapper,
            cas,
            sourceCatalogs
        );

        lenient().when(sourceCatalogs.isSourceUsePermitted(any(), any(), any())).thenReturn(true);

        Workspace workspace = new Workspace();
        ReflectionTestUtils.setField(workspace, "id", 1L);

        testPractice = new Practice();
        ReflectionTestUtils.setField(testPractice, "id", 10L);
        testPractice.setSlug("pr-description-quality");
        testPractice.setEvidence(PracticeTestEvidence.forArtifact(WorkArtifact.PULL_REQUEST));
        testPractice.setWorkspace(workspace);

        testJob = new AgentJob();
        ReflectionTestUtils.setField(testJob, "id", UUID.randomUUID());
        testJob.setWorkspace(workspace);
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("pull_request_id", 456L);
        metadata.put("repository_id", 123L);
        metadata.put("repository_full_name", "owner/repo");
        metadata.put("pr_number", 42);
        testJob.setMetadata(metadata);
        ObjectNode snapshot = objectMapper.createObjectNode();
        var sources = snapshot.putObject("manifest").put("contractVersion", "1.0.0").putArray("sources");
        var source = sources.addObject().put("kind", "scm.pull-request.diff").put("availability", "AVAILABLE");
        source
            .putArray("artifacts")
            .addObject()
            .put("path", "inputs/context/diff.patch")
            .put("sha256", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        sources
            .addObject()
            .put("kind", "scm.pull-request.core")
            .put("availability", "AVAILABLE")
            .putArray("artifacts")
            .addObject()
            .put("path", "inputs/context/pull_request.json")
            .put("sha256", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        snapshot.putArray("practices").addObject().put("slug", "pr-description-quality").put("revisionId", 11L);
        testJob.setEvidenceSnapshot(snapshot);

        PracticeRevision revision = org.mockito.Mockito.mock(PracticeRevision.class);
        lenient().when(revision.getId()).thenReturn(11L);
        lenient().when(revision.getSlug()).thenReturn("pr-description-quality");
        lenient().when(revision.getPractice()).thenReturn(testPractice);
        lenient().when(revision.getEvidence()).thenReturn(testPractice.getEvidence());
        lenient().when(practiceRevisionRepository.findById(11L)).thenReturn(Optional.of(revision));
        lenient()
            .when(cas.get("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
            .thenReturn(
                Optional.of(
                    "diff --git a/src/Auth.java b/src/Auth.java\n+++ b/src/Auth.java\n@@ -10 +10 @@\n[L10] + insecure();\n".getBytes(
                        StandardCharsets.UTF_8
                    )
                )
            );

        testAuthor = new User();
        ReflectionTestUtils.setField(testAuthor, "id", 789L);
        testAuthor.setLogin("developer");
        testPr = new PullRequest();
        testPr.setNumber(42);
        testPr.setAuthor(testAuthor);
        Repository repository = new Repository();
        ReflectionTestUtils.setField(repository, "id", 123L);
        repository.setNameWithOwner("owner/repo");
        testPr.setRepository(repository);

        lenient().when(pullRequestRepository.findByIdWithAuthorAndRepository(456L)).thenReturn(Optional.of(testPr));
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

    private ValidatedFinding validFinding(String slug, Presence presence) {
        Assessment assessment = switch (presence) {
            case PRESENT -> Assessment.GOOD;
            case ABSENT -> Assessment.BAD;
            case NOT_APPLICABLE -> null;
        };
        ObjectNode evidence = objectMapper.createObjectNode();
        evidence
            .putArray("citations")
            .addObject()
            .put("sourceKind", "scm.pull-request.diff")
            .put("artifactPath", "inputs/context/diff.patch")
            .put("path", "src/Auth.java")
            .put("side", "NEW")
            .put("startLine", 10)
            .put("endLine", 10)
            .put("quote", "+ insecure();");
        return new ValidatedFinding(
            slug,
            "Test finding",
            presence,
            assessment,
            Severity.INFO,
            0.9f,
            evidence,
            null,
            null,
            List.of()
        );
    }

    private void admit(Practice practice, long revisionId) {
        practice.setWorkspace(testPractice.getWorkspace());
        ((ObjectNode) testJob.getEvidenceSnapshot()).withArray("practices")
            .addObject()
            .put("slug", practice.getSlug())
            .put("revisionId", revisionId);
        PracticeRevision revision = org.mockito.Mockito.mock(PracticeRevision.class);
        lenient().when(revision.getId()).thenReturn(revisionId);
        lenient().when(revision.getSlug()).thenReturn(practice.getSlug());
        lenient().when(revision.getPractice()).thenReturn(practice);
        lenient().when(revision.getEvidence()).thenReturn(practice.getEvidence());
        lenient().when(practiceRevisionRepository.findById(revisionId)).thenReturn(Optional.of(revision));
    }

    @Nested
    class EvidenceBoundary {

        @Test
        void rejectsMissingSourceAttribution() {
            ValidatedFinding finding = validFinding("pr-description-quality", Presence.PRESENT);
            ((ObjectNode) finding.evidence()).remove("citations");

            assertThatThrownBy(() -> service.deliver(testJob, List.of(finding)))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("no source-bound evidence citation");
            verifyNoInteractions(observationRepository);
        }

        @Test
        void rejectsDiffCitationWithoutSide() {
            ValidatedFinding finding = validFinding("pr-description-quality", Presence.PRESENT);
            ((ObjectNode) finding.evidence().withArray("citations").get(0)).remove("side");

            assertThatThrownBy(() -> service.deliver(testJob, List.of(finding)))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("invalid evidence citation");
            verifyNoInteractions(observationRepository);
        }

        @Test
        void rejectsNonDiffCitationWithSide() {
            ValidatedFinding finding = validFinding("pr-description-quality", Presence.PRESENT);
            ObjectNode citation = (ObjectNode) finding.evidence().withArray("citations").get(0);
            citation.put("sourceKind", "scm.pull-request.core");
            citation.put("artifactPath", "inputs/context/pull_request.json");
            citation.put("path", "pull_request.json");

            assertThatThrownBy(() -> service.deliver(testJob, List.of(finding)))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("invalid evidence citation");
            verifyNoInteractions(observationRepository);
        }

        @Test
        void rejectsSourcesOutsideThePracticeDeclaration() {
            ValidatedFinding finding = validFinding("pr-description-quality", Presence.PRESENT);
            ObjectNode citation = (ObjectNode) finding.evidence().withArray("citations").get(0);
            citation.put("sourceKind", "scm.repository.tree");
            citation.remove("side");

            assertThatThrownBy(() -> service.deliver(testJob, List.of(finding)))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("misattributed evidence source");
            verifyNoInteractions(observationRepository);
        }

        @Test
        void rejectsASourceWithdrawnAfterCapture() {
            when(
                sourceCatalogs.isSourceUsePermitted(any(), any(), eq(SourceUseAudience.PRACTICE_FEEDBACK_RECIPIENTS))
            ).thenReturn(false);

            assertThatThrownBy(() ->
                service.deliver(testJob, List.of(validFinding("pr-description-quality", Presence.PRESENT)))
            )
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("authorization was withdrawn");
            verifyNoInteractions(observationRepository);
            verify(sourceCatalogs).isSourceUsePermitted(
                any(),
                any(),
                eq(SourceUseAudience.PRACTICE_FEEDBACK_RECIPIENTS)
            );
        }

        @Test
        void rejectsAnUncitedSourceWithdrawnAfterCapture() {
            when(
                sourceCatalogs.isSourceUsePermitted(any(), eq(new SourceKind("scm.pull-request.core")), any())
            ).thenReturn(false);

            assertThatThrownBy(() ->
                service.deliver(testJob, List.of(validFinding("pr-description-quality", Presence.PRESENT)))
            )
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("scm.pull-request.core");
            verifyNoInteractions(observationRepository);
        }

        @Test
        void rejectsAQuoteThatIsNotInTheCitedArtifact() {
            ValidatedFinding finding = validFinding("pr-description-quality", Presence.PRESENT);
            ((ObjectNode) ((ObjectNode) finding.evidence()).withArray("citations").get(0)).put(
                "quote",
                "fabricated quote"
            );

            assertThatThrownBy(() -> service.deliver(testJob, List.of(finding)))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("does not match the cited diff location");
            verifyNoInteractions(observationRepository);
        }

        @Test
        void acceptsASecretScannerCitationWithoutPersistingTheSecret() {
            ValidatedFinding finding = validFinding("pr-description-quality", Presence.PRESENT);
            ObjectNode evidence = (ObjectNode) finding.evidence();
            evidence.put("detector", "secret-diff-scanner");
            ObjectNode citation = (ObjectNode) evidence.withArray("citations").get(0);
            citation.remove("quote");
            citation.put("quoteSha256", "cbbe06955840924d2ccb449029560ae1eb92f5ec9866804f1a34be23b61dc488");

            assertThat(service.deliver(testJob, List.of(finding)).inserted()).isEqualTo(1);
            ArgumentCaptor<String> persistedEvidence = ArgumentCaptor.forClass(String.class);
            verify(observationRepository).insertIfAbsent(
                any(),
                anyString(),
                any(),
                anyLong(),
                any(),
                anyString(),
                anyLong(),
                anyLong(),
                any(),
                anyString(),
                any(),
                any(),
                anyFloat(),
                persistedEvidence.capture(),
                any(),
                anyString(),
                any()
            );
            assertThat(persistedEvidence.getValue()).doesNotContain("quoteSha256", "cbbe06955840924d");
        }

        @Test
        void rejectsAFabricatedSecretScannerDigest() {
            ValidatedFinding finding = validFinding("pr-description-quality", Presence.PRESENT);
            ObjectNode evidence = (ObjectNode) finding.evidence();
            evidence.put("detector", "secret-diff-scanner");
            ObjectNode citation = (ObjectNode) evidence.withArray("citations").get(0);
            citation.remove("quote");
            citation.put("quoteSha256", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

            assertThatThrownBy(() -> service.deliver(testJob, List.of(finding)))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("does not match the cited diff location");
            verifyNoInteractions(observationRepository);
        }

        @Test
        void rejectsARealQuoteAtTheWrongDiffLine() {
            ValidatedFinding finding = validFinding("pr-description-quality", Presence.PRESENT);
            ((ObjectNode) finding.evidence().withArray("citations").get(0)).put("startLine", 11);
            ((ObjectNode) finding.evidence().withArray("citations").get(0)).put("endLine", 11);

            assertThatThrownBy(() -> service.deliver(testJob, List.of(finding)))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("does not match the cited diff location");
            verifyNoInteractions(observationRepository);
        }

        @Test
        void rejectsARealQuoteInTheWrongDiffFile() {
            ValidatedFinding finding = validFinding("pr-description-quality", Presence.PRESENT);
            ((ObjectNode) finding.evidence().withArray("citations").get(0)).put("path", "src/Other.java");

            assertThatThrownBy(() -> service.deliver(testJob, List.of(finding)))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("does not match the cited diff location");
            verifyNoInteractions(observationRepository);
        }

        @Test
        void rejectsARealQuoteWithAnInvalidDiffRange() {
            ValidatedFinding finding = validFinding("pr-description-quality", Presence.PRESENT);
            ((ObjectNode) finding.evidence().withArray("citations").get(0)).put("endLine", 11);

            assertThatThrownBy(() -> service.deliver(testJob, List.of(finding)))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("does not match the cited diff location");
            verifyNoInteractions(observationRepository);
        }

        @Test
        void acceptsRemovedLineEvidenceOnTheOldSide() {
            when(cas.get("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")).thenReturn(
                Optional.of(
                    (
                        "diff --git a/src/Auth.java b/src/Auth.java\n" +
                        "--- a/src/Auth.java\n" +
                        "+++ b/src/Auth.java\n" +
                        "@@ -8 +8 @@\n" +
                        "[L8] - requireAdmin();\n" +
                        "[L8] + allowAll();\n"
                    ).getBytes(StandardCharsets.UTF_8)
                )
            );
            ValidatedFinding finding = validFinding("pr-description-quality", Presence.PRESENT);
            ObjectNode citation = (ObjectNode) finding.evidence().withArray("citations").get(0);
            citation.put("side", "OLD");
            citation.put("startLine", 8);
            citation.put("endLine", 8);
            citation.put("quote", "- requireAdmin();");

            assertThat(service.deliver(testJob, List.of(finding)).inserted()).isEqualTo(1);
        }

        @Test
        void rejectsACitationToAnUnavailableSource() {
            ((ObjectNode) testJob.getEvidenceSnapshot().path("manifest").path("sources").get(0)).put(
                "availability",
                "UNAVAILABLE"
            );

            assertThatThrownBy(() ->
                service.deliver(testJob, List.of(validFinding("pr-description-quality", Presence.PRESENT)))
            )
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("misattributed evidence source");
            verifyNoInteractions(observationRepository);
        }
    }

    @Nested
    class HappyPath {

        @Test
        void persistsValidFinding() {
            var findings = List.of(validFinding("pr-description-quality", Presence.PRESENT));

            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(1);
            assertThat(result.discardedDuplicate()).isZero();

            ArgumentCaptor<String> fingerprintCaptor = ArgumentCaptor.forClass(String.class);
            verify(observationRepository).insertIfAbsent(
                any(UUID.class),
                eq("pr-description-quality:0:PULL_REQUEST:456:" + testJob.getId()),
                eq(testJob.getId()),
                eq(10L),
                eq(11L),
                eq("PULL_REQUEST"),
                eq(456L),
                eq(789L), // aboutUserId
                eq("Test finding"),
                eq("PRESENT"), // presence (ADR 0022)
                eq("GOOD"), // assessment (former-GOOD practice, PRESENT → a strength)
                isNull(), // severity — coerced to null for a non-BAD finding (ADR 0022 invariant)
                eq(0.9f),
                anyString(),
                isNull(),
                fingerprintCaptor.capture(), // findingFingerprint == persisted recurrence_key
                any()
            );

            // The recurrence_key written to the row MUST equal the fingerprint the result map returns —
            // they are the single supersession identity, so any drift between them silently breaks re-review.
            assertThat(fingerprintCaptor.getValue())
                .as("persisted recurrence_key matches the returned findingFingerprint")
                .matches("[0-9a-f]{64}")
                .isEqualTo(result.observationKeys().values().iterator().next().recurrenceKey());

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

            assertThatThrownBy(() -> service.deliver(testJob, findings))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("not admitted");
            verifyNoInteractions(observationRepository);
        }
    }

    @Nested
    class TargetResolution {

        @Test
        @DisplayName("throws when pull request not found")
        void prNotFound() {
            when(pullRequestRepository.findByIdWithAuthorAndRepository(456L)).thenReturn(Optional.empty());
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

        @Test
        void mismatchedArtifactMetadataIsRejectedBeforePersistence() {
            ((ObjectNode) testJob.getMetadata()).put("repository_id", 999L);
            var findings = List.of(validFinding("pr-description-quality", Presence.PRESENT));

            assertThatThrownBy(() -> service.deliver(testJob, findings))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("does not match the live target");
            verifyNoInteractions(observationRepository, eventPublisher);
        }

        @Test
        void conversationTargetMustMatchTheLiveWorkspaceThreadAndParticipant() {
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("artifact_type", WorkArtifact.CONVERSATION_THREAD.name());
            metadata.put("slack_thread_id", 77L);
            metadata.put("slack_channel_id", "C123");
            metadata.put("slack_thread_ts", "1700000000.100000");
            metadata.put("about_user_id", 789L);
            testJob.setMetadata(metadata);

            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "resolveTarget", testJob, metadata))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("no longer authorized");
            verify(slackThreadRepository).existsDeliverableThread(77L, 1L, "C123", "1700000000.100000", 789L);
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
            otherPractice.setEvidence(PracticeTestEvidence.forArtifact(WorkArtifact.PULL_REQUEST));
            admit(otherPractice, 22L);

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
    class SeverityCoherence {

        /** Captures the severity (position 12) the native insert receives for one delivered finding. */
        private String capturedSeverityFor(ValidatedFinding finding) {
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
            otherPractice.setEvidence(PracticeTestEvidence.forArtifact(WorkArtifact.PULL_REQUEST));
            admit(otherPractice, 22L);

            var findings = List.of(
                validFinding("pr-description-quality", Presence.PRESENT),
                validFinding("error-handling", Presence.ABSENT)
            );

            service.deliver(testJob, findings);

            verify(eventPublisher).publishEvent(eventCaptor.capture());
            PracticeDetectionCompletedEvent event = eventCaptor.getValue();
            assertThat(event.findingsInserted()).isEqualTo(2);
            assertThat(event.findingsDiscarded()).isZero();
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
            issue.setNumber(12);
            Repository repository = new Repository();
            ReflectionTestUtils.setField(repository, "id", 123L);
            repository.setNameWithOwner("owner/repo");
            issue.setRepository(repository);
            when(issueRepository.findByIdWithAuthorAndRepository(999L)).thenReturn(Optional.of(issue));

            ObjectNode meta = new ObjectMapper().createObjectNode();
            meta.put("artifact_type", "ISSUE");
            meta.put("issue_id", 999L);
            meta.put("repository_id", 123L);
            meta.put("repository_full_name", "owner/repo");
            meta.put("issue_number", 12);
            testJob.setMetadata(meta);

            var findings = List.of(validFinding("pr-description-quality", Presence.ABSENT));
            var result = service.deliver(testJob, findings);

            assertThat(result.inserted()).isEqualTo(1);
            verify(observationRepository).insertIfAbsent(
                any(),
                eq("pr-description-quality:0:ISSUE:999:" + testJob.getId()),
                eq(testJob.getId()),
                anyLong(),
                eq(11L),
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
}
