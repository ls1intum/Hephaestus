package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.ContextManifestBuilder;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.EvidencePlan;
import de.tum.cit.aet.hephaestus.agent.context.PreparedEvidence;
import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionDeliveryService.DeliveryResult;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmission;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelopeWriter;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceManifest;
import de.tum.cit.aet.hephaestus.evidence.AutomatedReviewReadinessReport;
import de.tum.cit.aet.hephaestus.integration.core.events.RepositoryRef;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.integration.core.fabric.ContentAddressedStore;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaults;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class PullRequestReviewHandlerTest extends BaseUnitTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Mock
    private ContentAddressedStore cas;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private WorkspaceContextBuilder workspaceContextBuilder;

    @Mock
    private PracticeDetectionDeliveryService deliveryService;

    @Mock
    private FeedbackDeliveryService feedbackService;

    private static final Long WORKSPACE_ID = 99L;

    private PracticeDetectionResultParser resultParser;
    private TaskEnvelopeWriter taskEnvelopeWriter;
    private PullRequestReviewHandler handler;

    @BeforeEach
    void setUp() {
        resultParser = new PracticeDetectionResultParser(objectMapper);
        taskEnvelopeWriter = new TaskEnvelopeWriter(objectMapper);
        handler = new PullRequestReviewHandler(
            objectMapper,
            cas,
            new PracticeCatalogInjector(objectMapper, practiceRepository, workspaceDefaults()),
            workspaceContextBuilder,
            taskEnvelopeWriter,
            resultParser,
            deliveryService,
            feedbackService,
            new SecretDiffScanner(),
            // Real flag-OFF filter: evaluate() returns the findings unchanged without touching the repos.
            new ReactionSuppressionFilter(
                org.mockito.Mockito.mock(de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.class),
                org.mockito.Mockito.mock(
                    de.tum.cit.aet.hephaestus.practices.observation.reaction.ReactionRepository.class
                ),
                org.mockito.Mockito.mock(FeedbackLedgerRecorder.class),
                new de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties(
                    false,
                    false,
                    15,
                    5,
                    false,
                    false
                )
            ),
            // Real gate over the same mocked catalogue: with no practice rows, every slug is unknown and
            // therefore admitted, so these tests exercise delivery rather than the tier.
            new InContextDeliveryGate(
                practiceRepository,
                org.mockito.Mockito.mock(de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.class),
                org.mockito.Mockito.mock(FeedbackLedgerRecorder.class),
                workspaceDefaults()
            )
        );
        lenient().when(cas.get(anyString())).thenReturn(java.util.Optional.of(new byte[0]));
    }

    private PullRequestReviewSubmissionRequest sampleRequest() {
        var pullRequestData = new ScmEventPayload.PullRequestData(
            456L,
            42,
            "Fix authentication bug",
            "This PR fixes the login issue",
            Issue.State.OPEN,
            false,
            false,
            10,
            5,
            3,
            "https://github.com/owner/repo/pull/42",
            new RepositoryRef(123L, "owner/repo", "main"),
            789L,
            Instant.now(),
            Instant.now(),
            null,
            null,
            null
        );
        return new PullRequestReviewSubmissionRequest(pullRequestData, "feature/auth-fix", "abc123def456", "main");
    }

    private ObjectNode sampleJobMetadata() {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("repository_id", 123L);
        metadata.put("repository_full_name", "owner/repo");
        metadata.put("pull_request_id", 456L);
        metadata.put("pr_number", 42);
        metadata.put("pr_url", "https://github.com/owner/repo/pull/42");
        metadata.put("commit_sha", "abc123def456");
        metadata.put("source_branch", "feature/auth-fix");
        metadata.put("target_branch", "main");
        return metadata;
    }

    private AgentJob jobWithMetadata(ObjectNode metadata) {
        var job = new AgentJob();
        job.setId(UUID.randomUUID());
        job.setMetadata(metadata);
        job.setEvidenceSnapshot(admittedPracticeSnapshot());
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        job.setWorkspace(workspace);
        return job;
    }

    private ObjectNode admittedPracticeSnapshot() {
        ObjectNode snapshot = objectMapper.createObjectNode();
        var practices = snapshot.putArray("practices");
        practices.addObject().put("slug", "pr-description-quality").put("revisionId", 1).put("defectDetector", false);
        practices.addObject().put("slug", "error-handling").put("revisionId", 2).put("defectDetector", false);
        practices
            .addObject()
            .put("slug", "avoids-insecure-defaults-and-over-broad-permissions")
            .put("revisionId", 3)
            .put("defectDetector", true);
        var source = snapshot
            .putObject("manifest")
            .putArray("sources")
            .addObject()
            .put("kind", "scm.pull-request.diff");
        source.putObject("state").put("availability", "AVAILABLE").put("content", "NON_EMPTY");
        source
            .putArray("artifacts")
            .addObject()
            .put("path", "inputs/context/diff.patch")
            .put("sha256", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        return snapshot;
    }

    private Practice createPractice(String slug, String name, String criteria) {
        Practice p = new Practice();
        p.setId((long) slug.hashCode());
        p.setSlug(slug);
        p.setName(name);
        p.setCriteria(criteria);
        p.setReviewTier(PracticeReviewTier.DELIVER);
        p.setBindings(PracticeTestEvidence.bindings(ArtifactKinds.PULL_REQUEST));
        p.setAutomatedReviewPolicy(PracticeTestEvidence.forArtifact(ArtifactKinds.PULL_REQUEST));
        var revision = new PracticeRevision();
        ReflectionTestUtils.setField(revision, "id", Math.abs((long) slug.hashCode()) + 1);
        p.setCurrentRevision(revision);
        return p;
    }

    private List<Practice> samplePractices() {
        return List.of(
            createPractice("pr-description-quality", "PR Description Quality", "criteria"),
            createPractice("error-handling", "Error Handling", "fallback criteria")
        );
    }

    /** Stub the workspace context build + practice catalog to return minimal valid data. */
    private void stubDefaults() {
        lenient()
            .when(
                workspaceContextBuilder.prepare(
                    any(ContextRequest.PracticeReviewRequest.class),
                    any(EvidencePlan.class)
                )
            )
            .thenReturn(prepared(Map.of("inputs/context/metadata.json", "{}".getBytes(StandardCharsets.UTF_8))));
        lenient()
            .when(workspaceContextBuilder.prepareAutomatedReviewReadiness(any(), any(), anyString(), any(), any()))
            .thenAnswer(invocation -> readiness(invocation.getArgument(1)));
        lenient()
            .when(practiceRepository.findByWorkspaceIdAndArtifactKind(WORKSPACE_ID, ArtifactKinds.PULL_REQUEST))
            .thenReturn(samplePractices());
    }

    private PreparedEvidence prepared(Map<String, byte[]> files) {
        return new PreparedEvidence(files, org.mockito.Mockito.mock(ArtifactSourceManifest.class));
    }

    private ContextManifestBuilder.PreparedAutomatedReviewReadiness readiness(List<Practice> practices) {
        return new ContextManifestBuilder.PreparedAutomatedReviewReadiness(
            practices,
            mock(AutomatedReviewReadinessReport.class)
        );
    }

    @Nested
    class JobType {

        @Test
        void returnsPullRequestReview() {
            assertThat(handler.jobType()).isEqualTo(AgentJobType.PULL_REQUEST_REVIEW);
        }
    }

    @Nested
    class CreateSubmission {

        @Test
        void extractsMetadata() {
            JobSubmission submission = handler.createSubmission(sampleRequest());
            JsonNode metadata = submission.metadata();

            assertThat(metadata.get("repository_id").asLong()).isEqualTo(123L);
            assertThat(metadata.get("repository_full_name").asString()).isEqualTo("owner/repo");
            assertThat(metadata.get("pr_number").asInt()).isEqualTo(42);
            assertThat(metadata.get("commit_sha").asString()).isEqualTo("abc123def456");
            // The MR title + description are the only inputs for the process practices
            // (describe-what-and-why, commit-subjects-explain-each-change); a regression here makes them
            // silently un-evaluable.
            assertThat(metadata.get("title").asString()).isEqualTo("Fix authentication bug");
            assertThat(metadata.get("body").asString()).isEqualTo("This PR fixes the login issue");
            // No triggerEvent in sampleRequest() → phase segment is "manual"; head SHA stays the trailing
            // freshness slot so extractCooldownKeyPrefix scopes cooldown per (pr, phase), not per push.
            assertThat(submission.idempotencyKey()).isEqualTo("pr_review:owner/repo:42:manual:abc123def456");
        }

        @Test
        void rejectsWrongRequestType() {
            JobSubmissionRequest wrongType = new JobSubmissionRequest() {};
            assertThatThrownBy(() -> handler.createSubmission(wrongType))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected PullRequestReviewSubmissionRequest");
        }
    }

    @Nested
    class PrepareInputs {

        @Test
        void delegatesToWorkspaceContextBuilder() {
            stubDefaults();
            AgentJob job = jobWithMetadata(sampleJobMetadata());

            handler.prepareInputs(job);

            ArgumentCaptor<ContextRequest> captor = ArgumentCaptor.forClass(ContextRequest.class);
            verify(workspaceContextBuilder).prepare(captor.capture(), any(EvidencePlan.class));
            assertThat(captor.getValue()).isInstanceOf(ContextRequest.PracticeReviewRequest.class);
            assertThat(((ContextRequest.PracticeReviewRequest) captor.getValue()).job()).isSameAs(job);
        }

        @Test
        void mergesProviderFiles() {
            byte[] metadataBytes = "{\"pr_number\":42}".getBytes(StandardCharsets.UTF_8);
            when(
                workspaceContextBuilder.prepare(
                    any(ContextRequest.PracticeReviewRequest.class),
                    any(EvidencePlan.class)
                )
            ).thenReturn(prepared(Map.of("inputs/context/metadata.json", metadataBytes)));
            when(
                workspaceContextBuilder.prepareAutomatedReviewReadiness(any(), any(), anyString(), any(), any())
            ).thenAnswer(invocation -> readiness(invocation.getArgument(1)));
            when(
                practiceRepository.findByWorkspaceIdAndArtifactKind(WORKSPACE_ID, ArtifactKinds.PULL_REQUEST)
            ).thenReturn(samplePractices());

            Map<String, byte[]> files = handler.prepareInputs(jobWithMetadata(sampleJobMetadata())).files();

            assertThat(files.get("inputs/context/metadata.json")).isEqualTo(metadataBytes);
        }

        @Test
        void writesTaskJsonEnvelope() throws Exception {
            stubDefaults();
            Map<String, byte[]> files = handler.prepareInputs(jobWithMetadata(sampleJobMetadata())).files();

            assertThat(files).containsKey("task.json");
            JsonNode envelope = objectMapper.readTree(files.get("task.json"));
            assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
            assertThat(envelope.get("workspaceId").asLong()).isEqualTo(WORKSPACE_ID);
            JsonNode task = envelope.get("task");
            assertThat(task.get("kind").asString()).isEqualTo("practice_review");
            assertThat(task.get("pullRequestNumber").asInt()).isEqualTo(42);
            assertThat(task.get("repositoryFullName").asString()).isEqualTo("owner/repo");
            assertThat(task.get("prompt").asString()).contains("Review merge request #42");
        }

        @Test
        void injectsPracticeCatalog() {
            stubDefaults();
            Map<String, byte[]> files = handler.prepareInputs(jobWithMetadata(sampleJobMetadata())).files();

            assertThat(files).containsKey("inputs/practices/index.json");
            assertThat(files).containsKey("inputs/practices/all-criteria.md");
            assertThat(files).containsKey("inputs/practices/pr-description-quality.md");
            assertThat(files).containsKey("inputs/practices/error-handling.md");
            assertThat(files).containsKey("work/analysis/practices/.gitkeep");
        }

        @Test
        void doesNotWriteLegacyPromptFile() {
            stubDefaults();
            Map<String, byte[]> files = handler.prepareInputs(jobWithMetadata(sampleJobMetadata())).files();
            assertThat(files).doesNotContainKey(".prompt");
        }

        @Test
        void rejectsMalformedSlug() {
            when(
                practiceRepository.findByWorkspaceIdAndArtifactKind(WORKSPACE_ID, ArtifactKinds.PULL_REQUEST)
            ).thenReturn(List.of(createPractice("../etc/passwd", "bad", "c")));

            assertThatThrownBy(() -> handler.prepareInputs(jobWithMetadata(sampleJobMetadata())))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("Practice slug fails ABI pattern");
        }

        @Test
        void throwsWhenNoActivePractices() {
            when(
                practiceRepository.findByWorkspaceIdAndArtifactKind(WORKSPACE_ID, ArtifactKinds.PULL_REQUEST)
            ).thenReturn(List.of());

            assertThatThrownBy(() -> handler.prepareInputs(jobWithMetadata(sampleJobMetadata())))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("No active scm.pull_request practices");
            verifyNoInteractions(workspaceContextBuilder);
        }

        @Test
        void throwsWhenMetadataMissing() {
            var job = new AgentJob();
            job.setMetadata(null);
            assertThatThrownBy(() -> handler.prepareInputs(job))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("no metadata");
        }

        @Test
        void preservesProviderOrder() {
            var providerFiles = new LinkedHashMap<String, byte[]>();
            providerFiles.put("inputs/context/metadata.json", "{}".getBytes(StandardCharsets.UTF_8));
            providerFiles.put("inputs/context/diff.patch", "diff".getBytes(StandardCharsets.UTF_8));
            providerFiles.put("inputs/context/comments.json", "[]".getBytes(StandardCharsets.UTF_8));
            when(workspaceContextBuilder.prepare(any(), any())).thenReturn(prepared(providerFiles));
            when(
                workspaceContextBuilder.prepareAutomatedReviewReadiness(any(), any(), anyString(), any(), any())
            ).thenAnswer(invocation -> readiness(invocation.getArgument(1)));
            when(
                practiceRepository.findByWorkspaceIdAndArtifactKind(WORKSPACE_ID, ArtifactKinds.PULL_REQUEST)
            ).thenReturn(samplePractices());

            Map<String, byte[]> files = handler.prepareInputs(jobWithMetadata(sampleJobMetadata())).files();

            // First three entries must be the provider files in their original order
            var keys = files.keySet().iterator();
            assertThat(keys.next()).isEqualTo("inputs/context/metadata.json");
            assertThat(keys.next()).isEqualTo("inputs/context/diff.patch");
            assertThat(keys.next()).isEqualTo("inputs/context/comments.json");
        }
    }

    @Nested
    class ParseDiffNameOnlyPaths {

        @Test
        void simplePaths() {
            String output = "src/Main.swift\nViews/ContentView.swift\nREADME.md\n";
            assertThat(PullRequestReviewHandler.parseDiffNameOnlyPaths(output)).containsExactlyInAnyOrder(
                "src/Main.swift",
                "Views/ContentView.swift",
                "README.md"
            );
        }

        @Test
        void blankInput() {
            assertThat(PullRequestReviewHandler.parseDiffNameOnlyPaths("")).isEmpty();
            assertThat(PullRequestReviewHandler.parseDiffNameOnlyPaths("  \n  ")).isEmpty();
        }
    }

    @Nested
    class Deliver {

        private AgentJob jobWithOutput(String rawOutputJson) {
            var job = new AgentJob();
            job.setId(UUID.randomUUID());
            job.setEvidenceSnapshot(admittedPracticeSnapshot());
            ObjectNode output = objectMapper.createObjectNode();
            output.put("rawOutput", rawOutputJson);
            job.setOutput(output);
            return job;
        }

        @Test
        @SuppressWarnings("unchecked")
        void delegatesToDeliveryService() {
            String rawOutput = """
                {
                  "findings": [{
                    "practiceSlug": "pr-description-quality",
                    "title": "Good PR description",
                    "presence": "PRESENT",
                    "assessment": "GOOD",
                    "severity": "INFO",
                    "confidence": 0.95
                  }]
                }
                """;
            AgentJob job = jobWithOutput(rawOutput);
            when(deliveryService.deliver(eq(job), any())).thenReturn(new DeliveryResult(1, 0, false, Map.of()));

            handler.deliver(job);

            verify(deliveryService).deliver(eq(job), any());
            verify(feedbackService).deliverFeedback(eq(job), any(), any());
        }

        @Test
        void throwsWhenNoValidFindings() {
            AgentJob job = jobWithOutput("{\"findings\":[]}");
            assertThatThrownBy(() -> handler.deliver(job))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("No valid findings");
        }

        @Test
        @SuppressWarnings("unchecked")
        void hardcodedSecretUsesPracticeSeverityCap() {
            String rawOutput = """
                {
                  "findings": [{
                    "practiceSlug": "avoids-insecure-defaults-and-over-broad-permissions",
                    "title": "Hard-coded credential",
                    "presence": "PRESENT",
                    "assessment": "BAD",
                    "severity": "CRITICAL",
                    "confidence": 0.99,
                    "reasoning": "A live API key is committed.",
                    "evidence": { "citations": [{ "path": "Sources/Config.swift", "startLine": 3 }] }
                  }]
                }
                """;
            AgentJob job = jobWithOutput(rawOutput);
            ArgumentCaptor<List<PracticeDetectionResultParser.ValidatedFinding>> captor = ArgumentCaptor.forClass(
                List.class
            );
            when(deliveryService.deliver(eq(job), captor.capture())).thenReturn(
                new DeliveryResult(1, 0, false, Map.of())
            );

            handler.deliver(job);

            List<PracticeDetectionResultParser.ValidatedFinding> delivered = captor.getValue();
            var secret = delivered
                .stream()
                .filter(f -> "avoids-insecure-defaults-and-over-broad-permissions".equals(f.practiceSlug()))
                .findFirst()
                .orElseThrow();
            assertThat(secret.severity()).isEqualTo(Severity.MAJOR);
        }

        private void stubDiff(String diff) {
            String annotated =
                de.tum.cit.aet.hephaestus.agent.context.providers.GitDiffOperations.annotateDiffWithLineNumbers(diff);
            when(cas.get(anyString())).thenReturn(java.util.Optional.of(annotated.getBytes(StandardCharsets.UTF_8)));
        }

        @Test
        void throwsWhenAllNotApplicableButDiffHasFiles() {
            // Stale/empty-diff refuse-to-deliver: every finding is NOT_APPLICABLE yet the diff lists changed
            // files — the agent was handed a stale diff. Refusing here stops a misleading "nothing applies"
            // post over an artifact that did change.
            String rawOutput = """
                {
                  "findings": [{
                    "practiceSlug": "pr-description-quality",
                    "title": "Not applicable here",
                    "presence": "NOT_APPLICABLE",
                    "assessment": "GOOD",
                    "severity": "INFO",
                    "confidence": 0.9
                  }]
                }
                """;
            AgentJob job = jobWithMetadata(sampleJobMetadata());
            ObjectNode output = objectMapper.createObjectNode();
            output.put("rawOutput", rawOutput);
            job.setOutput(output);
            stubDiff(
                "diff --git a/Sources/Auth.swift b/Sources/Auth.swift\n+++ b/Sources/Auth.swift\n@@ -1 +1 @@\n+changed\n"
            );

            assertThatThrownBy(() -> handler.deliver(job))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("stale/empty diff");
            verifyNoInteractions(deliveryService);
        }

        @Test
        void throwsWhenAllFindingsFilteredByDiffScope() {
            // The only finding cites a file that is NOT in the diff — the diff-scope filter drops it, leaving
            // nothing to deliver. That is a refuse-to-deliver, not a silent empty post.
            String rawOutput = """
                {
                  "findings": [{
                    "practiceSlug": "error-handling",
                    "title": "Unhandled error path",
                    "presence": "ABSENT",
                    "assessment": "BAD",
                    "severity": "MAJOR",
                    "confidence": 0.9,
                    "reasoning": "The error branch is swallowed.",
                    "guidance": "Surface the error.",
                    "evidence": { "citations": [{ "path": "Sources/NotInDiff.swift", "startLine": 3 }] }
                  }]
                }
                """;
            AgentJob job = jobWithMetadata(sampleJobMetadata());
            ObjectNode output = objectMapper.createObjectNode();
            output.put("rawOutput", rawOutput);
            job.setOutput(output);
            // Diff touches a DIFFERENT file, so the finding's location is out of scope.
            stubDiff(
                "diff --git a/Sources/Other.swift b/Sources/Other.swift\n+++ b/Sources/Other.swift\n@@ -1 +1 @@\n+x\n"
            );

            assertThatThrownBy(() -> handler.deliver(job))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("filtered by diff scope");
            verifyNoInteractions(deliveryService);
        }

        @Test
        @SuppressWarnings("unchecked")
        void injectsSecretFindingWhenModelAbstainsButDiffCommitsCredential() {
            String rawOutput = """
                {
                  "findings": [{
                    "practiceSlug": "pr-description-quality",
                    "title": "Clear description",
                    "presence": "PRESENT",
                    "assessment": "GOOD",
                    "severity": "INFO",
                    "confidence": 0.9
                  }]
                }
                """;
            AgentJob job = jobWithMetadata(sampleJobMetadata());
            ObjectNode output = objectMapper.createObjectNode();
            output.put("rawOutput", rawOutput);
            job.setOutput(output);
            stubDiff(
                "diff --git a/Sources/Config.swift b/Sources/Config.swift\n" +
                    "+++ b/Sources/Config.swift\n" +
                    "@@ -1 +1,2 @@\n" +
                    "+let key = \"AKIA1234567890ABCDEF\"\n"
            );

            ArgumentCaptor<List<PracticeDetectionResultParser.ValidatedFinding>> captor = ArgumentCaptor.forClass(
                List.class
            );
            when(deliveryService.deliver(eq(job), captor.capture())).thenReturn(
                new DeliveryResult(1, 0, false, Map.of())
            );

            handler.deliver(job);

            List<PracticeDetectionResultParser.ValidatedFinding> delivered = captor.getValue();
            var secret = delivered
                .stream()
                .filter(f -> "avoids-insecure-defaults-and-over-broad-permissions".equals(f.practiceSlug()))
                .findFirst()
                .orElseThrow();
            assertThat(secret.presence()).isEqualTo(Presence.PRESENT);
            assertThat(secret.assessment()).isEqualTo(Assessment.BAD);
            assertThat(secret.reasoning()).doesNotContain("AKIA1234567890ABCDEF");
            JsonNode evidence = secret.evidence();
            assertThat(evidence.toString()).doesNotContain("AKIA1234567890ABCDEF");
            assertThat(evidence.path("detector").asString()).isEqualTo("secret-diff-scanner");
            JsonNode citation = evidence.path("citations").get(0);
            assertThat(citation.has("quote")).isFalse();
            assertThat(citation.path("quoteSha256").asString()).isEqualTo(
                "b2b88104bf5c02259227480b0eabe2f9b7d63501e03e788b7b82a499b818e12a"
            );
        }

        @Test
        @SuppressWarnings("unchecked")
        void stampsDeliveryObservationFingerprintOntoComposedDiffNote() {
            String rawOutput = """
                {
                  "findings": [{
                    "practiceSlug": "error-handling",
                    "title": "Unhandled error path",
                    "presence": "ABSENT",
                    "assessment": "BAD",
                    "severity": "MAJOR",
                    "confidence": 0.9,
                    "reasoning": "The error branch is swallowed.",
                    "guidance": "Surface the error to the caller.",
                    "evidence": { "citations": [{
                      "sourceKind": "scm.pull-request.diff",
                      "artifactPath": "inputs/context/diff.patch",
                      "path": "Sources/Auth.swift",
                      "side": "NEW",
                      "startLine": 12,
                      "endLine": 12,
                      "quote": "throw error",
                      "quoteRedacted": false
                    }] }
                  }]
                }
                """;
            AgentJob job = jobWithMetadata(sampleJobMetadata());
            ObjectNode output = objectMapper.createObjectNode();
            output.put("rawOutput", rawOutput);
            job.setOutput(output);
            stubDiff(
                "diff --git a/Sources/Auth.swift b/Sources/Auth.swift\n" +
                    "--- a/Sources/Auth.swift\n" +
                    "+++ b/Sources/Auth.swift\n" +
                    "@@ -11,0 +12 @@\n" +
                    "+throw error\n"
            );

            when(deliveryService.deliver(eq(job), any())).thenAnswer(invocation -> {
                List<PracticeDetectionResultParser.ValidatedFinding> received = invocation.getArgument(1);
                Map<PracticeDetectionResultParser.ValidatedFinding, ObservationKeys> keys =
                    new java.util.IdentityHashMap<>();
                for (var f : received) {
                    keys.put(f, new ObservationKeys("occ-" + f.practiceSlug(), "corr-" + f.practiceSlug()));
                }
                return new DeliveryResult(received.size(), 0, true, keys);
            });

            handler.deliver(job);

            ArgumentCaptor<PracticeDetectionResultParser.DeliveryContent> captor = ArgumentCaptor.forClass(
                PracticeDetectionResultParser.DeliveryContent.class
            );
            verify(feedbackService).deliverFeedback(eq(job), captor.capture(), any());
            PracticeDetectionResultParser.DeliveryContent delivered = captor.getValue();
            assertThat(delivered).isNotNull();
            assertThat(delivered.diffNotes()).hasSize(1);
            assertThat(delivered.diffNotes().get(0).recurrenceKey()).isEqualTo("corr-error-handling");
        }
    }

    /**
     * Resolves every workspace to the unset defaults — DELIVER autonomy, reach on the work — which is what
     * a workspace that has never configured anything gets, and therefore what these fixtures meant before
     * the chain existed.
     */
    private static WorkspaceReviewDefaultsProvider workspaceDefaults() {
        WorkspaceReviewDefaultsProvider provider = mock(WorkspaceReviewDefaultsProvider.class);
        lenient().when(provider.forWorkspace(anyLong())).thenReturn(WorkspaceReviewDefaults.UNSET);
        return provider;
    }
}
