package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.context.EvidenceDirectory;
import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.JobFolderIndex;
import de.tum.cit.aet.hephaestus.agent.context.PreparedEvidence;
import de.tum.cit.aet.hephaestus.agent.handler.ObservationAdmissionService;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeFeedbackDeliveryPolicy;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedJobInputs;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.evidence.AutomatedReviewReadinessReport;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.RepositoryKey;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyStage;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stages a writer that continues an admitted review from its admission's original inputs: the records and whole
 * standards the lost writer had, the original commits from the local mirror, and the admitted observations. It
 * captures nothing and assesses nothing; the runner reads {@link #CONTINUATION_PATH} and only composes the review
 * on the work. Every refusal happens here, before any model runs.
 */
@Component
public class ReviewCompositionContinuation {

    /** What the runner composes from instead of an admission response: present only on a continuing attempt. */
    public static final String CONTINUATION_PATH = SandboxLayout.INPUTS_PREFIX + "composition-continuation.json";

    private static final String REPOSITORY_TREE = "scm.repository.tree";
    private static final String DIFF = "scm.pull-request.diff";
    private static final String GIT_DIRECTORY = SandboxLayout.REPO_MOUNT_RELATIVE + ".git/";

    private final ReviewCompositionBasisRepository bases;
    private final JobEvidenceFiles evidenceFiles;
    private final AgentJobRepository jobs;
    private final GitRepositoryManager git;
    private final ObservationAdmissionService admission;
    private final PracticeFeedbackDeliveryPolicy policy;
    private final JsonMapper mapper = new JsonMapper();

    public ReviewCompositionContinuation(
            ReviewCompositionBasisRepository bases,
            JobEvidenceFiles evidenceFiles,
            GitRepositoryManager git,
            ObservationAdmissionService admission,
            AgentJobRepository jobs,
            PracticeFeedbackDeliveryPolicy policy) {
        this.bases = bases;
        this.evidenceFiles = evidenceFiles;
        this.git = git;
        this.admission = admission;
        this.jobs = jobs;
        this.policy = policy;
    }

    /** Call first in the drain/orphan transaction, before the existing owned-job lock. */
    public void holdRecoveryCustody() {
        evidenceFiles.holdCopyAdmission();
    }

    /** Reuses the original copy's suppression authority before any accounting reset or token rotation. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int requeue(AgentJob job, String workerId, AgentJobRepository.CompositionRequeue request) {
        long workspaceId = job.getWorkspace().getId();
        var copy = bases.findOriginalCopy(workspaceId, job.getId()).orElse(null);
        if (copy == null
                || !evidenceFiles.vouchesForOriginalCopy(
                        copy.copyId(), job.getId(), workspaceId, copy.admissionAttempt())) return 0;
        if (jobs.lockContinuableAttempt(
                        job.getId(), workerId, request.attempt(), ReviewCompositionBasis.VERSION, request.now())
                .isEmpty()) return 0;
        return jobs.requeueLockedForComposition(job.getId(), workerId, request);
    }

    public PreparedJobInputs prepareInputs(AgentJob job) {
        var basis = bases.findByWorkspaceIdAndJobId(job.getWorkspace().getId(), job.getId())
                .orElseThrow(() -> unavailable("its original inputs are gone"));
        Map<String, byte[]> files = originalFiles(job, basis);
        JobFolderIndex manifest = readManifest(files);
        JsonNode recordedReadiness = job.getReviewReadiness();
        if (recordedReadiness == null) throw unavailable("its original readiness report is gone");
        AutomatedReviewReadinessReport readiness =
                mapper.treeToValue(recordedReadiness, AutomatedReviewReadinessReport.class);
        requireEligiblePublicWork(job);
        evidenceFiles.beginPersonCapture(job);
        GitRepositoryManager.GitTreeSnapshot snapshot = null;
        try {
            try {
                evidenceFiles.inheritPersonCapture(job, basis.getCopyId(), basis.getAdmissionAttempt());
            } catch (IllegalStateException erased) {
                throw new JobPreparationException(
                        "The review cannot continue: its original inputs are no longer vouched for", erased);
            }
            var staged = new LinkedHashMap<String, byte[]>();
            files.forEach((path, bytes) -> {
                if (!path.startsWith(SandboxLayout.REPO_MOUNT_RELATIVE)) staged.put(path, bytes);
            });
            JsonNode admitted = admission.admittedResponse(job);
            staged.put(CONTINUATION_PATH, mapper.writeValueAsBytes(admitted));
            var directories = new ArrayList<EvidenceDirectory>();
            var cleanups = new ArrayList<AutoCloseable>();
            String tree = identity(manifest, REPOSITORY_TREE);
            if (tree != null) {
                snapshot = restore(job, manifest, tree, files, admitted);
                directories.add(new EvidenceDirectory(SandboxLayout.REPO_MOUNT_RELATIVE, snapshot.stagingDir()));
                cleanups.add(snapshot);
            }
            return evidenceFiles.prepare(
                    job, new PreparedEvidence(staged, Map.of(), cleanups, manifest, directories), readiness);
        } catch (RuntimeException exception) {
            if (snapshot != null) snapshot.close();
            evidenceFiles.abortPersonCapture(job);
            throw exception;
        }
    }

    /** Earlier reads only avoid a known refusal; the final locked delivery comparison remains authoritative. */
    private void requireEligiblePublicWork(AgentJob job) {
        var decision = ArtifactKinds.ISSUE.equals(job.getArtifactKind())
                ? policy.evaluateIssue(job, DeliveryPolicyStage.COMPOSITION, null)
                : policy.evaluatePullRequest(job, DeliveryPolicyStage.COMPOSITION, null);
        if (!decision.allowed()) throw unavailable(decision.refusal().name());
        if (policy.currentReviewedRevision(job, null) == PracticeFeedbackDeliveryPolicy.ReviewedRevision.CHANGED) {
            throw unavailable(FeedbackSuppressionReason.REVIEWED_REVISION_CHANGED.name());
        }
    }

    /** Removes the kept inputs once the review's result is recorded. */
    @Transactional
    public void forget(AgentJob job) {
        bases.deleteForJob(job.getWorkspace().getId(), job.getId());
    }

    /**
     * The kept inputs, only if they are the admission's own, unexpired, exactly as they were kept, and the manifest and
     * practice index the job's evidence snapshot recorded when that attempt was staged.
     */
    private Map<String, byte[]> originalFiles(AgentJob job, ReviewCompositionBasis basis) {
        String digest = Objects.requireNonNull(job.getMetadata())
                .path(ObservationAdmissionService.DIGEST_METADATA_KEY)
                .asString("");
        if (basis.getBasisVersion() != ReviewCompositionBasis.VERSION
                || !basis.getExpiresAt().isAfter(Instant.now())
                || !basis.getAdmissionDigest().equals(digest)
                || basis.getAdmissionAttempt() != ObservationAdmissionService.citationAttempt(job)) {
            throw unavailable("its original inputs no longer belong to its admission");
        }
        var files = new TreeMap<String, byte[]>();
        basis.getPayload()
                .path("files")
                .properties()
                .forEach(entry -> files.put(
                        entry.getKey(),
                        Base64.getDecoder().decode(entry.getValue().asString())));
        if (!ReviewCompositionBasis.digest(files).equals(basis.getBasisDigest())) {
            throw unavailable("its original inputs changed after they were kept");
        }
        JsonNode snapshot = job.getEvidenceSnapshot();
        byte[] manifest = files.get(SandboxLayout.MANIFEST_PATH);
        byte[] practices = files.get(SandboxLayout.PRACTICES_PREFIX + "index.json");
        if (snapshot == null
                || manifest == null
                || !originalManifestMatches(manifest, snapshot.path("manifest"))
                || (practices == null
                        ? snapshot.has("practices")
                        : !snapshot.path("practices").equals(mapper.readTree(practices)))) {
            throw unavailable("its original inputs are not the ones its evidence recorded");
        }
        return files;
    }

    /** Admission retires only inventories; compare through the existing retained capture facts contract. */
    private boolean originalManifestMatches(byte[] original, JsonNode recorded) {
        try {
            return mapper.readValue(original, JobFolderIndex.class)
                    .retained()
                    .equals(mapper.treeToValue(recorded, JobFolderIndex.Retained.class));
        } catch (JacksonException | IllegalArgumentException invalid) {
            return false;
        }
    }

    private JobFolderIndex readManifest(Map<String, byte[]> files) {
        byte[] index = files.get(SandboxLayout.MANIFEST_PATH);
        if (index == null) throw unavailable("its original inputs hold no manifest");
        return mapper.readValue(index, JobFolderIndex.class);
    }

    /**
     * The original commits from the local mirror, with the checkout's own recorded HEAD and refs. Nothing is fetched
     * and no current ref is read: a commit the mirror no longer holds, or a tree that differs, refuses the attempt.
     */
    private GitRepositoryManager.GitTreeSnapshot restore(
            AgentJob job, JobFolderIndex manifest, String tree, Map<String, byte[]> files, JsonNode admitted) {
        String[] commitAndTree = tree.split(":", -1);
        JsonNode repositoryId = Objects.requireNonNull(job.getMetadata()).path("repository_id");
        if (commitAndTree.length != 2 || !repositoryId.isIntegralNumber()) {
            throw unavailable("its original repository is not identified");
        }
        var commits = new ArrayList<String>();
        String range = identity(manifest, DIFF);
        if (range != null) commits.addAll(List.of(range.split(":", -1)));
        // A repository quote can name any commit the original checkout held, not only the change's endpoints.
        for (JsonNode observation : admitted.path("observations")) {
            for (JsonNode citation : observation.path("citations")) {
                if (REPOSITORY_TREE.equals(citation.path("sourceKind").asString())
                        && (GIT_DIRECTORY + "HEAD")
                                .equals(citation.path("artifactPath").asString())
                        && "VERIFIED"
                                .equals(citation.path("verification")
                                        .path("status")
                                        .asString())
                        && citation.path("revision").isString()) {
                    commits.add(citation.path("revision").asString());
                }
            }
        }
        GitRepositoryManager.GitTreeSnapshot snapshot;
        try {
            snapshot = git.readOriginalSnapshot(
                    new RepositoryKey(job.getWorkspace().getId(), repositoryId.asLong()), commitAndTree[0], commits);
        } catch (GitRepositoryManager.GitOperationException missing) {
            throw new JobPreparationException(
                    "The review cannot continue: its original commits are no longer available here", missing);
        }
        if (!snapshot.treeSha().equals(commitAndTree[1])) {
            snapshot.close();
            throw unavailable("its original tree does not match");
        }
        try {
            for (var file : files.entrySet()) {
                if (!file.getKey().startsWith(GIT_DIRECTORY)) continue;
                Path target = snapshot.stagingDir()
                        .resolve(file.getKey().substring(SandboxLayout.REPO_MOUNT_RELATIVE.length()));
                Files.write(target, file.getValue());
            }
        } catch (IOException exception) {
            snapshot.close();
            throw new UncheckedIOException(exception);
        }
        return snapshot;
    }

    /** The immutable identity the capture recorded for an available source, if it recorded one. */
    private static @Nullable String identity(JobFolderIndex manifest, String kind) {
        return manifest.sources().stream()
                .filter(source -> source.kind().value().equals(kind))
                .map(source -> source.state() instanceof SourceCaptureState.Available available
                        ? available.facts().immutableIdentity()
                        : null)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static JobPreparationException unavailable(String why) {
        return new JobPreparationException("The review cannot continue: " + why);
    }
}
