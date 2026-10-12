package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.JobFolderIndex;
import de.tum.cit.aet.hephaestus.agent.context.PreparedEvidence;
import de.tum.cit.aet.hephaestus.agent.context.ReviewedWorkFixtures;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeFeedbackDeliveryPolicy;
import de.tum.cit.aet.hephaestus.agent.handler.ResultAdmissionService;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedJobInputs;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.evidence.AutomatedReviewReadinessReport;
import de.tum.cit.aet.hephaestus.evidence.SourceCapture;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureFacts;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.RepositoryKey;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyStage;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** A continuing writer receives the admission's own original inputs, or nothing: every refusal precedes capture. */
class ReviewCompositionContinuationTest extends BaseUnitTest {

    private final String manifest = new JsonMapper()
            .writeValueAsString(ReviewedWorkFixtures.issueManifest(Instant.parse("2026-10-01T00:00:00Z"), "Original"));

    private final JsonMapper mapper = new JsonMapper();

    @Mock
    private ReviewCompositionBasisRepository bases;

    @Mock
    private JobEvidenceFiles evidenceFiles;

    @Mock
    private GitRepositoryManager git;

    @Mock
    private ResultAdmissionService admission;

    @Mock
    private AgentJobRepository jobs;

    @Mock
    private PracticeFeedbackDeliveryPolicy policy;

    private ReviewCompositionContinuation continuation;
    private AgentJob job;
    private ReviewCompositionBasis basis;

    @BeforeEach
    void setUp() {
        continuation = new ReviewCompositionContinuation(bases, evidenceFiles, git, admission, jobs, policy);
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        job = new AgentJob();
        job.setId(UUID.randomUUID());
        job.setWorkspace(workspace);
        job.setRetryCount(1);
        job.setJobType(AgentJobType.ISSUE_REVIEW);
        job.setArtifactKind(AgentJobService.artifactKindFor(AgentJobType.ISSUE_REVIEW));
        job.setMetadata(mapper.createObjectNode()
                .put(ResultAdmissionService.DIGEST_METADATA_KEY, "admitted-digest")
                .put(ResultAdmissionService.ATTEMPT_METADATA_KEY, 0));
        job.setEvidenceSnapshot(mapper.createObjectNode().set("manifest", mapper.readTree(manifest)));
        String report = """
                {"contractVersion":"1.2.0","catalogDigest":"%s","artifactKind":"issue",
                 "manifestCapturedAt":"2026-10-01T00:00:00Z","decidedAt":"2026-10-01T00:00:00Z",
                 "decisions":[{"practiceSlug":"practice","decidedAt":"2026-10-01T00:00:00Z",
                   "ready":false,"reasonCodes":["NO_AUTOMATED_REVIEW"],"sourceChecks":[]}]}
                """.formatted("0".repeat(64));
        job.setReviewReadiness(mapper.readTree(report));
        Map<String, byte[]> files = Map.of(
                SandboxLayout.MANIFEST_PATH,
                manifest.getBytes(StandardCharsets.UTF_8),
                SandboxLayout.CONTEXT_PREFIX + "description.md",
                "Original".getBytes(StandardCharsets.UTF_8));
        ObjectNode payload = mapper.createObjectNode();
        ObjectNode stored = payload.putObject("files");
        files.forEach((path, bytes) -> stored.put(path, Base64.getEncoder().encodeToString(bytes)));
        basis = new ReviewCompositionBasis();
        basis.setJobId(job.getId());
        basis.setWorkspaceId(1L);
        basis.setAdmissionAttempt(0);
        basis.setAdmissionDigest("admitted-digest");
        basis.setBasisVersion(ReviewCompositionBasis.VERSION);
        basis.setBasisDigest(ReviewCompositionBasis.digest(files));
        basis.setCopyId(UUID.randomUUID());
        basis.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        basis.setPayload(payload);
    }

    @ParameterizedTest
    @CsvSource({"ISSUE_REVIEW, CURRENT", "PULL_REQUEST_REVIEW, CURRENT", "PULL_REQUEST_REVIEW, UNKNOWN"})
    void shouldStageTheOriginalRecordsWhenPolicyAllowsAndNoHeadChangeIsKnown(
            AgentJobType type, PracticeFeedbackDeliveryPolicy.ReviewedRevision revision) {
        job.setJobType(type);
        job.setArtifactKind(AgentJobService.artifactKindFor(type));
        allowPublicWork();
        when(policy.currentReviewedRevision(job, null)).thenReturn(revision);

        // The real admission retires file inventories while preserving these capture facts.
        job.setEvidenceSnapshot(mapper.createObjectNode()
                .set(
                        "manifest",
                        mapper.valueToTree(
                                mapper.readValue(manifest, JobFolderIndex.class).retained())));
        when(bases.findByWorkspaceIdAndJobId(1L, job.getId())).thenReturn(Optional.of(basis));
        ObjectNode admitted = mapper.createObjectNode().put("digest", "admitted-digest");
        when(admission.admittedResponse(job)).thenReturn(admitted);
        when(evidenceFiles.prepare(eq(job), any(), any())).thenAnswer(invocation -> {
            PreparedEvidence inputs = invocation.getArgument(1);
            AutomatedReviewReadinessReport readiness = invocation.getArgument(2);
            return new PreparedJobInputs(Map.of(), List.of(), List.of(), inputs.manifest(), readiness);
        });

        try (PreparedJobInputs prepared = continuation.prepareInputs(job)) {
            assertThat(prepared.automatedReviewReadinessReport())
                    .isEqualTo(mapper.treeToValue(job.getReviewReadiness(), AutomatedReviewReadinessReport.class));
        }
        ArgumentCaptor<PreparedEvidence> staged = ArgumentCaptor.forClass(PreparedEvidence.class);
        verify(evidenceFiles).prepare(eq(job), staged.capture(), any());
        assertThat(staged.getValue().files().get(SandboxLayout.CONTEXT_PREFIX + "description.md"))
                .isEqualTo("Original".getBytes(StandardCharsets.UTF_8));
        assertThat(mapper.readTree(staged.getValue().files().get(ReviewCompositionContinuation.CONTINUATION_PATH)))
                .isEqualTo(admitted);
        verify(evidenceFiles).inheritPersonCapture(job, basis.getCopyId(), 0);
        verify(git, never()).readOriginalSnapshot(any(), any(), any());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"result", "problem", "example", "open question"})
    void shouldRefuseWhenAnAdmittedRepositoryQuoteIsNoLongerInTheMirror(String citedBy) {
        String head = "a".repeat(40);
        String cited = "b".repeat(40);
        Instant capturedAt = Instant.parse("2026-10-01T00:00:00Z");
        JobFolderIndex original = mapper.readValue(manifest, JobFolderIndex.class);
        var sources = new ArrayList<>(original.sources());
        sources.add(new SourceCapture(
                new SourceKind("scm.repository.tree"),
                new SourceCaptureState.Available(
                        SourceContentState.EMPTY,
                        SourceCompleteness.COMPLETE,
                        new SourceCaptureFacts(capturedAt, null, null, head + ":" + "c".repeat(40))),
                List.of()));
        var withRepository = new JobFolderIndex(
                original.contractVersion(),
                original.catalogDigest(),
                original.artifactKind(),
                original.capturedAt(),
                sources);
        byte[] index = mapper.writeValueAsBytes(withRepository);
        Map<String, byte[]> files = Map.of(
                SandboxLayout.MANIFEST_PATH,
                index,
                SandboxLayout.CONTEXT_PREFIX + "description.md",
                "Original".getBytes(StandardCharsets.UTF_8));
        ObjectNode payload = mapper.createObjectNode();
        ObjectNode stored = payload.putObject("files");
        files.forEach((path, bytes) -> stored.put(path, Base64.getEncoder().encodeToString(bytes)));
        basis.setPayload(payload);
        basis.setBasisDigest(ReviewCompositionBasis.digest(files));
        job.setEvidenceSnapshot(
                mapper.createObjectNode().set("manifest", mapper.valueToTree(withRepository.retained())));
        ((ObjectNode) Objects.requireNonNull(job.getMetadata())).put("repository_id", 3);
        ObjectNode admitted = mapper.createObjectNode().put("schemaVersion", 2);
        ObjectNode result = admitted.putArray("results")
                .addObject()
                .put("practiceResultId", UUID.randomUUID().toString());
        ObjectNode citation =
                switch (citedBy) {
                    case "result" -> result.putArray("citations").addObject();
                    case "problem" ->
                        result.putArray("observations")
                                .addObject()
                                .put("observationId", UUID.randomUUID().toString())
                                .putArray("citations")
                                .addObject();
                    case "example" -> result.putObject("example").putObject("citation");
                    default ->
                        result.putObject("evidence")
                                .putArray("undecidability")
                                .addObject()
                                .put("openQuestion", "Does the release notes page list this change?")
                                .putObject("citation");
                };
        citation.put("sourceKind", "scm.repository.tree")
                .put("artifactPath", SandboxLayout.REPO_MOUNT_RELATIVE + ".git/HEAD")
                .put("revision", cited)
                .putObject("verification")
                .put("status", "VERIFIED");
        when(bases.findByWorkspaceIdAndJobId(1L, job.getId())).thenReturn(Optional.of(basis));
        when(admission.admittedResponse(job)).thenReturn(admitted);
        allowPublicWork();
        when(policy.currentReviewedRevision(job, null))
                .thenReturn(PracticeFeedbackDeliveryPolicy.ReviewedRevision.CURRENT);
        when(git.readOriginalSnapshot(
                        eq(new RepositoryKey(1L, 3L)),
                        eq(head),
                        ArgumentMatchers.<Collection<String>>argThat(commits -> commits.contains(cited))))
                .thenThrow(
                        new GitRepositoryManager.GitOperationException("Missing original commit", new IOException()));

        assertThatThrownBy(() -> continuation.prepareInputs(job))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining("original commits are no longer available");
        verify(evidenceFiles).abortPersonCapture(job);
        verify(evidenceFiles, never()).prepare(any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({
        "ISSUE_REVIEW, ARTIFACT_CLOSED",
        "PULL_REQUEST_REVIEW, ARTIFACT_CLOSED",
        "PULL_REQUEST_REVIEW, ARTIFACT_MERGED"
    })
    void shouldRefuseCurrentPolicyBeforeStaging(AgentJobType type, FeedbackSuppressionReason reason) {
        job.setJobType(type);
        job.setArtifactKind(AgentJobService.artifactKindFor(type));
        when(bases.findByWorkspaceIdAndJobId(1L, job.getId())).thenReturn(Optional.of(basis));
        if (type == AgentJobType.ISSUE_REVIEW) {
            when(policy.evaluateIssue(job, DeliveryPolicyStage.COMPOSITION, null))
                    .thenReturn(new PracticeFeedbackDeliveryPolicy.Decision<>(null, reason));
        } else {
            when(policy.evaluatePullRequest(job, DeliveryPolicyStage.COMPOSITION, null))
                    .thenReturn(new PracticeFeedbackDeliveryPolicy.Decision<>(null, reason));
        }

        assertThatThrownBy(() -> continuation.prepareInputs(job))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining(reason.name());
        verify(evidenceFiles, never()).beginPersonCapture(any());
        verify(policy, never()).currentReviewedRevision(any(), any());
    }

    @Test
    void shouldRefuseAKnownHeadChangeBeforeStaging() {
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setArtifactKind(AgentJobService.artifactKindFor(AgentJobType.PULL_REQUEST_REVIEW));
        when(bases.findByWorkspaceIdAndJobId(1L, job.getId())).thenReturn(Optional.of(basis));
        allowPublicWork();
        when(policy.currentReviewedRevision(job, null))
                .thenReturn(PracticeFeedbackDeliveryPolicy.ReviewedRevision.CHANGED);

        assertThatThrownBy(() -> continuation.prepareInputs(job))
                .isInstanceOf(JobPreparationException.class)
                .hasMessageContaining(FeedbackSuppressionReason.REVIEWED_REVISION_CHANGED.name());
        verify(evidenceFiles, never()).beginPersonCapture(any());
    }

    private void allowPublicWork() {
        if (job.getJobType() == AgentJobType.ISSUE_REVIEW) {
            when(policy.evaluateIssue(job, DeliveryPolicyStage.COMPOSITION, null))
                    .thenReturn(new PracticeFeedbackDeliveryPolicy.Decision<>(new Issue(), null));
        } else {
            when(policy.evaluatePullRequest(job, DeliveryPolicyStage.COMPOSITION, null))
                    .thenReturn(new PracticeFeedbackDeliveryPolicy.Decision<>(new PullRequest(), null));
        }
    }

    @Test
    void shouldRefuseALegacyAdmissionThatKeptNoInputs() {
        when(bases.findByWorkspaceIdAndJobId(1L, job.getId())).thenReturn(Optional.empty());

        assertRefusedBeforeCapture();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"tampered", "expired", "another admission", "another attempt", "another manifest"})
    void shouldRefuseInputsThatAreNotTheAdmissionsOwnAsKept(String why) {
        switch (why) {
            case "tampered" ->
                ((ObjectNode) basis.getPayload().path("files"))
                        .put(
                                SandboxLayout.CONTEXT_PREFIX + "description.md",
                                Base64.getEncoder().encodeToString("Changed".getBytes(StandardCharsets.UTF_8)));
            case "expired" -> basis.setExpiresAt(Instant.now().minusSeconds(1));
            case "another admission" -> basis.setAdmissionDigest("other-digest");
            case "another attempt" -> basis.setAdmissionAttempt(1);
            default ->
                job.setEvidenceSnapshot(mapper.createObjectNode()
                        .set("manifest", mapper.createObjectNode().put("other", true)));
        }
        when(bases.findByWorkspaceIdAndJobId(1L, job.getId())).thenReturn(Optional.of(basis));

        assertRefusedBeforeCapture();
    }

    private void assertRefusedBeforeCapture() {
        assertThatThrownBy(() -> continuation.prepareInputs(job)).isInstanceOf(JobPreparationException.class);
        verify(evidenceFiles, never()).beginPersonCapture(any());
        verify(git, never()).readOriginalSnapshot(any(), any(), any());
    }
}
