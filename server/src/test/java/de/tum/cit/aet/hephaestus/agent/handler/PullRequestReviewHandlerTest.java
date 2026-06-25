package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.context.providers.GitDiffOperations;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionDeliveryService.DeliveryResult;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmission;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelopeWriter;
import de.tum.cit.aet.hephaestus.integration.core.events.RepositoryRef;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class PullRequestReviewHandlerTest extends BaseUnitTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Mock
    private GitRepositoryManager gitRepositoryManager;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private WorkspaceContextBuilder workspaceContextBuilder;

    @Mock
    private GitDiffOperations gitDiffOperations;

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
            gitRepositoryManager,
            new PracticeCatalogInjector(objectMapper, practiceRepository),
            workspaceContextBuilder,
            taskEnvelopeWriter,
            gitDiffOperations,
            resultParser,
            deliveryService,
            feedbackService,
            new SecretDiffScanner(),
            // Real flag-OFF filter: evaluate() returns the findings unchanged without touching the repos.
            new ReactionSuppressionFilter(
                org.mockito.Mockito.mock(de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository.class),
                org.mockito.Mockito.mock(
                    de.tum.cit.aet.hephaestus.practices.finding.reaction.FindingReactionRepository.class
                ),
                org.mockito.Mockito.mock(FeedbackLedgerRecorder.class),
                new de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties(
                    false,
                    true,
                    false,
                    "",
                    15,
                    false,
                    false,
                    false
                )
            )
        );
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
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        job.setWorkspace(workspace);
        return job;
    }

    private Practice createPractice(String slug, String name, String criteria) {
        Practice p = new Practice();
        p.setId((long) slug.hashCode());
        p.setSlug(slug);
        p.setName(name);
        p.setCriteria(criteria);
        p.setActive(true);
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
            .when(workspaceContextBuilder.build(any(ContextRequest.PracticeReviewRequest.class)))
            .thenReturn(
                new LinkedHashMap<>(Map.of("inputs/context/metadata.json", "{}".getBytes(StandardCharsets.UTF_8)))
            );
        lenient()
            .when(
                practiceRepository.findByWorkspaceIdAndActiveTrueAndArtifactType(
                    WORKSPACE_ID,
                    de.tum.cit.aet.hephaestus.practices.model.WorkArtifact.PULL_REQUEST
                )
            )
            .thenReturn(samplePractices());
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
            // (mr-description-quality, commit-discipline); a regression here makes them silently un-evaluable.
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
    class PrepareInputFiles {

        @Test
        void delegatesToWorkspaceContextBuilder() {
            stubDefaults();
            AgentJob job = jobWithMetadata(sampleJobMetadata());

            handler.prepareInputFiles(job);

            ArgumentCaptor<ContextRequest> captor = ArgumentCaptor.forClass(ContextRequest.class);
            verify(workspaceContextBuilder).build(captor.capture());
            assertThat(captor.getValue()).isInstanceOf(ContextRequest.PracticeReviewRequest.class);
            assertThat(((ContextRequest.PracticeReviewRequest) captor.getValue()).job()).isSameAs(job);
        }

        @Test
        void mergesProviderFiles() {
            byte[] metadataBytes = "{\"pr_number\":42}".getBytes(StandardCharsets.UTF_8);
            when(workspaceContextBuilder.build(any(ContextRequest.PracticeReviewRequest.class))).thenReturn(
                new LinkedHashMap<>(Map.of("inputs/context/metadata.json", metadataBytes))
            );
            when(
                practiceRepository.findByWorkspaceIdAndActiveTrueAndArtifactType(
                    WORKSPACE_ID,
                    de.tum.cit.aet.hephaestus.practices.model.WorkArtifact.PULL_REQUEST
                )
            ).thenReturn(samplePractices());

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            assertThat(files.get("inputs/context/metadata.json")).isEqualTo(metadataBytes);
        }

        @Test
        void writesTaskJsonEnvelope() throws Exception {
            stubDefaults();
            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

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
            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            assertThat(files).containsKey("inputs/practices/index.json");
            assertThat(files).containsKey("inputs/practices/all-criteria.md");
            assertThat(files).containsKey("inputs/practices/pr-description-quality.md");
            assertThat(files).containsKey("inputs/practices/error-handling.md");
            assertThat(files).containsKey("work/analysis/practices/.gitkeep");
        }

        @Test
        void doesNotWriteLegacyPromptFile() {
            stubDefaults();
            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));
            assertThat(files).doesNotContainKey(".prompt");
        }

        @Test
        void rejectsMalformedSlug() {
            when(workspaceContextBuilder.build(any())).thenReturn(new LinkedHashMap<>());
            when(
                practiceRepository.findByWorkspaceIdAndActiveTrueAndArtifactType(
                    WORKSPACE_ID,
                    de.tum.cit.aet.hephaestus.practices.model.WorkArtifact.PULL_REQUEST
                )
            ).thenReturn(List.of(createPractice("../etc/passwd", "bad", "c")));

            assertThatThrownBy(() -> handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata())))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("Practice slug fails ABI pattern");
        }

        @Test
        void throwsWhenNoActivePractices() {
            when(workspaceContextBuilder.build(any())).thenReturn(new LinkedHashMap<>());
            when(
                practiceRepository.findByWorkspaceIdAndActiveTrueAndArtifactType(
                    WORKSPACE_ID,
                    de.tum.cit.aet.hephaestus.practices.model.WorkArtifact.PULL_REQUEST
                )
            ).thenReturn(List.of());

            assertThatThrownBy(() -> handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata())))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("No active PULL_REQUEST practices");
        }

        @Test
        void throwsWhenMetadataMissing() {
            var job = new AgentJob();
            job.setMetadata(null);
            assertThatThrownBy(() -> handler.prepareInputFiles(job))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("no metadata");
        }

        @Test
        void preservesProviderOrder() {
            var providerFiles = new LinkedHashMap<String, byte[]>();
            providerFiles.put("inputs/context/metadata.json", "{}".getBytes(StandardCharsets.UTF_8));
            providerFiles.put("inputs/context/diff.patch", "diff".getBytes(StandardCharsets.UTF_8));
            providerFiles.put("inputs/context/comments.json", "[]".getBytes(StandardCharsets.UTF_8));
            when(workspaceContextBuilder.build(any())).thenReturn(providerFiles);
            when(
                practiceRepository.findByWorkspaceIdAndActiveTrueAndArtifactType(
                    WORKSPACE_ID,
                    de.tum.cit.aet.hephaestus.practices.model.WorkArtifact.PULL_REQUEST
                )
            ).thenReturn(samplePractices());

            Map<String, byte[]> files = handler.prepareInputFiles(jobWithMetadata(sampleJobMetadata()));

            // First three entries must be the provider files in their original order
            var keys = files.keySet().iterator();
            assertThat(keys.next()).isEqualTo("inputs/context/metadata.json");
            assertThat(keys.next()).isEqualTo("inputs/context/diff.patch");
            assertThat(keys.next()).isEqualTo("inputs/context/comments.json");
        }
    }

    @Nested
    class FilterByDiffScope {

        @Test
        void keepsFindingInDiff() {
            var finding = finding("fatal-error-crash", Presence.ABSENT, "Sources/View.swift");
            var filtered = PullRequestReviewHandler.filterByDiffScope(List.of(finding), Set.of("Sources/View.swift"));
            assertThat(filtered).containsExactly(finding);
        }

        @Test
        void keepsFindingBackedByMetadata() {
            var finding = finding("mr-description-quality", Presence.ABSENT, "inputs/context/metadata.json");
            var filtered = PullRequestReviewHandler.filterByDiffScope(List.of(finding), Set.of("Sources/View.swift"));
            assertThat(filtered).containsExactly(finding);
        }

        @Test
        void filtersFindingBackedByNonWhitelistedInternal() {
            // contributor_history.json is an internal context file but NOT in ALLOWED_INTERNAL_CONTEXT_PATHS
            // (unlike comments.json, which reviewer practices legitimately cite as evidence and must survive).
            var finding = finding("review-noise", Presence.ABSENT, "inputs/context/contributor_history.json");
            var filtered = PullRequestReviewHandler.filterByDiffScope(List.of(finding), Set.of("Sources/View.swift"));
            assertThat(filtered).isEmpty();
        }

        @Test
        void filtersFindingOutsideDiff() {
            var finding = finding("view-logic-separation", Presence.ABSENT, "Sources/Other.swift");
            var filtered = PullRequestReviewHandler.filterByDiffScope(List.of(finding), Set.of("Sources/View.swift"));
            assertThat(filtered).isEmpty();
        }

        private PracticeDetectionResultParser.ValidatedFinding finding(
            String slug,
            Presence presence,
            String path
        ) {
            // Former-GOOD practice convention: PRESENT→GOOD, ABSENT→BAD, NOT_APPLICABLE→null.
            Assessment assessment = switch (presence) {
                case PRESENT -> Assessment.GOOD;
                case ABSENT -> Assessment.BAD;
                case NOT_APPLICABLE -> null;
            };
            return new PracticeDetectionResultParser.ValidatedFinding(
                slug,
                "title",
                presence,
                assessment,
                Severity.MINOR,
                0.8f,
                objectMapper
                    .createObjectNode()
                    .set(
                        "locations",
                        objectMapper.createArrayNode().add(objectMapper.createObjectNode().put("path", path))
                    ),
                null,
                null,
                List.of()
            );
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
            when(deliveryService.deliver(eq(job), any())).thenReturn(new DeliveryResult(1, 0, 0, false));

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
        void stampsDeliveryFindingFingerprintOntoComposedDiffNote() {
            // A NOT_OBSERVED finding with a code location synthesizes an inline diff note. The key deliver()
            // persisted must be threaded onto that note (not recomputed), so the composed DeliveryContent the
            // handler hands to FeedbackDeliveryService carries it. Fails against a no-op (key would be null).
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
                    "evidence": { "locations": [{ "path": "Sources/Auth.swift", "startLine": 12 }] }
                  }]
                }
                """;
            AgentJob job = jobWithMetadata(sampleJobMetadata());
            ObjectNode output = objectMapper.createObjectNode();
            output.put("rawOutput", rawOutput);
            job.setOutput(output);

            // Stub deliver() to return the SAME identity-keyed map the real service would: key every finding
            // it received with a deterministic correlation key derived from the instance the handler passed.
            when(deliveryService.deliver(eq(job), any())).thenAnswer(invocation -> {
                List<PracticeDetectionResultParser.ValidatedFinding> received = invocation.getArgument(1);
                Map<PracticeDetectionResultParser.ValidatedFinding, String> keys = new java.util.IdentityHashMap<>();
                for (var f : received) {
                    keys.put(f, "corr-" + f.practiceSlug());
                }
                return new DeliveryResult(received.size(), 0, 0, true, keys);
            });

            handler.deliver(job);

            ArgumentCaptor<PracticeDetectionResultParser.DeliveryContent> captor = ArgumentCaptor.forClass(
                PracticeDetectionResultParser.DeliveryContent.class
            );
            verify(feedbackService).deliverFeedback(eq(job), captor.capture(), any());
            PracticeDetectionResultParser.DeliveryContent delivered = captor.getValue();
            assertThat(delivered).isNotNull();
            assertThat(delivered.diffNotes()).hasSize(1);
            assertThat(delivered.diffNotes().get(0).findingFingerprint()).isEqualTo("corr-error-handling");
        }
    }
}
