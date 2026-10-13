package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.handler.ObservationAdmissionService.AdmissionIdentity;
import de.tum.cit.aet.hephaestus.agent.handler.ObservationAdmissionService.ReviewedTarget;
import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedReview;
import de.tum.cit.aet.hephaestus.agent.handler.composition.FeedbackCompositionInputs;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.ReviewCompositionBasisRepository;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonProcessingSuppression;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatch;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import java.io.Serial;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Accepts the whole review on the work of a running pull request or issue review, once its public writer finished and
 * before its private composition ends. The review is read against the job's own persisted admission, never the
 * submitter's copy of it, and decided by the same delivery gates as a terminal delivery. One short transaction then
 * commits the {@link PublicReviewCompletion} together with what the review puts on its way: the automatic package's
 * dispatch intent, the approval item, or the quiet and withheld ledger rows. The package is sent after that commit by
 * the existing dispatcher, whose final reservation still decides consent, freshness and cancellation; its recovery scan
 * sends it if this process stops first.
 *
 * <p>The job stays running: its private composition, terminal status and usage settle at the existing terminal
 * boundary. The first acceptance is final; an explicit refusal is recorded on the job, so its terminal output carries
 * no review onto the work either.
 */
@Service
public class PublicReviewCompletionService {

    /** The job metadata key that records an explicit refusal; see {@link #wasRefused}. */
    public static final String REFUSAL_METADATA_KEY = "public_review_refusal";

    static final String NOT_ADMITTED = "The review does not rest on this job's admitted observations.";
    static final String NO_PUBLIC_WORK = "This review has no review on the work.";
    static final String NOT_OWED = "This review owes no review on the work.";
    static final String BREAKS_CONTRACT = "The review breaks the whole-review contract.";
    static final String UNDECIDED = "The review leaves a publicly eligible problem undecided.";
    static final String UNVERIFIED = "The review rests on an observation whose citations are not verified.";
    static final String INADMISSIBLE = "The review cannot be admitted onto the work as written.";
    static final String COPY_GONE = "The original review inputs no longer authorize this review.";
    static final String PREVIOUSLY_REFUSED = "The review on this work was already refused.";
    static final String CHANGED = "A different review on the work was already accepted.";

    private final ObservationAdmissionService admission;
    private final AgentJobRepository jobs;
    private final PublicReviewCompletionInputs inputs;
    private final FeedbackResponseSuppressionFilter responseSuppression;
    private final InContextDeliveryGate inContextDeliveryGate;
    private final FeedbackDeliveryService feedbackDelivery;
    private final PublicReviewCompletionRepository completions;
    private final ReviewCompositionBasisRepository bases;
    private final JobEvidenceFiles evidenceFiles;
    private final PullRequestRepository pullRequests;
    private final PersonProcessingSuppression personSuppression;
    private final TransactionTemplate transactions;

    PublicReviewCompletionService(
            ObservationAdmissionService admission,
            AgentJobRepository jobs,
            PublicReviewCompletionInputs inputs,
            FeedbackResponseSuppressionFilter responseSuppression,
            InContextDeliveryGate inContextDeliveryGate,
            FeedbackDeliveryService feedbackDelivery,
            PublicReviewCompletionRepository completions,
            ReviewCompositionBasisRepository bases,
            JobEvidenceFiles evidenceFiles,
            PullRequestRepository pullRequests,
            PersonProcessingSuppression personSuppression,
            PlatformTransactionManager transactionManager) {
        this.admission = admission;
        this.jobs = jobs;
        this.inputs = inputs;
        this.responseSuppression = responseSuppression;
        this.inContextDeliveryGate = inContextDeliveryGate;
        this.feedbackDelivery = feedbackDelivery;
        this.completions = completions;
        this.bases = bases;
        this.evidenceFiles = evidenceFiles;
        this.pullRequests = pullRequests;
        this.personSuppression = personSuppression;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** Whether the job's review on the work was explicitly refused when it was submitted. */
    public static boolean wasRefused(AgentJob job) {
        return job.getMetadata() != null && job.getMetadata().has(REFUSAL_METADATA_KEY);
    }

    /**
     * Accepts {@code review} for the attempt that owns the job, or refuses it with a fixed reason.
     *
     * @throws ObservationAdmissionService.StaleAttemptException when the attempt no longer owns the job
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Outcome complete(AdmissionIdentity identity, String admissionDigest, @Nullable JsonNode review) {
        AgentJob candidate = Objects.requireNonNull(transactions.execute(status -> admission.ownedJob(identity)));
        ReviewedTarget target = ReviewedTarget.of(candidate);
        Accepted accepted;
        try {
            accepted = Objects.requireNonNull(transactions.execute(status -> {
                // Copy admission first, then the work, then the job: the order erasure and every writer of this work
                // take them in. The job is not held while waiting, so the key is the candidate's, checked once it is.
                evidenceFiles.holdCopyAdmission();
                if (target != null) pullRequests.lockById(target.pullRequestId());
                AgentJob job = admission.ownedJob(identity);
                if (!Objects.equals(target, ReviewedTarget.of(job))) {
                    throw new ObservationAdmissionService.StaleAttemptException();
                }
                return accept(identity, job, admissionDigest, review);
            }));
        } catch (Refused refused) {
            if (refused.recorded()) recordRefusal(identity, refused.reason());
            return Outcome.refused(refused.reason());
        }
        FeedbackDispatch intent = accepted.intent();
        if (intent != null) feedbackDelivery.sendAcceptedPackage(accepted.job(), intent);
        return Outcome.accepted(accepted.reviewDigest());
    }

    private Accepted accept(
            AdmissionIdentity identity, AgentJob job, String admissionDigest, @Nullable JsonNode submitted) {
        if (wasRefused(job)) throw new Refused(PREVIOUSLY_REFUSED, false);
        String admitted = ObservationAdmissionService.admissionDigest(job);
        if (admitted.isBlank() || !admitted.equals(admissionDigest)) throw new Refused(NOT_ADMITTED, true);
        ArtifactKind artifact = publicWorkOf(job);
        long workspaceId = identity.workspaceId();
        var prepared = inputs.prepare(job, submitted, admission.admittedResponse(job));
        List<Observation> persisted = prepared.observations();
        Set<UUID> publicIds = prepared.publicIds();
        Set<FeedbackChannel> channels = ArtifactKinds.ISSUE.equals(artifact)
                ? IssueReviewHandler.ISSUE_REVIEW_CHANNELS
                : FeedbackCompositionInputs.EVENT_REVIEW_CHANNELS;
        ComposedReview review = prepared.review();
        JsonNode normalized = prepared.normalized();
        String reviewDigest = prepared.digest();
        // The accepted identity is final, independently of its delivery state. Current admitted inputs must still be
        // readable to acknowledge a repeat.
        var existing = completions.findByWorkspaceIdAndJobId(workspaceId, job.getId());
        if (existing.isPresent()) {
            String accepted = existing.get().getReviewDigest();
            if (!accepted.equals(reviewDigest)) throw new Refused(CHANGED, false);
            return new Accepted(job, accepted, null);
        }
        var copy = bases.findOriginalCopy(workspaceId, job.getId()).orElse(null);
        if (copy == null
                || copy.admissionAttempt() != ObservationAdmissionService.citationAttempt(job)
                || !evidenceFiles.vouchesForOriginalCopy(
                        copy.copyId(), job.getId(), workspaceId, copy.admissionAttempt())
                || personSuppression.isReviewJobSuppressed(job.getId())) throw new Refused(COPY_GONE, true);
        if (!PullRequestReviewHandler.reviewOwed(job, persisted, publicIds, channels)) {
            throw new Refused(NOT_OWED, true);
        }
        if (review == null || normalized == null || reviewDigest == null) throw new Refused(BREAKS_CONTRACT, true);
        if (PullRequestReviewHandler.leavesUndecided(review, persisted, publicIds)) {
            throw new Refused(UNDECIDED, true);
        }

        List<ReviewResultParser.ValidatedObservation> recorded;
        FeedbackDispatch intent;
        try {
            recorded = persisted.stream()
                    .map(observation -> {
                        CitationVerification.requireVerified(job, observation.getEvidence());
                        return PullRequestReviewHandler.validated(observation);
                    })
                    .toList();
        } catch (JobDeliveryException unverified) {
            throw new Refused(UNVERIFIED, true, unverified);
        }
        try {
            // The terminal delivery's gate sequence, with every row it writes joining this transaction.
            List<ReviewResultParser.ValidatedObservation> eligible = responseSuppression
                    .evaluateInCurrentTransaction(job, recorded)
                    .deliverable();
            List<ReviewResultParser.ValidatedObservation> proposals =
                    inContextDeliveryGate.awaitingApproval(job, eligible);
            List<ReviewResultParser.ValidatedObservation> automatic =
                    inContextDeliveryGate.admitInContextInCurrentTransaction(job, eligible);
            AdmittedDelivery decision = AdmittedDelivery.decide(
                    review,
                    artifact,
                    recorded,
                    PullRequestReviewHandler.subjectsOf(persisted, publicIds),
                    proposals,
                    automatic);
            intent = feedbackDelivery.prepareAcceptedOutcome(job, artifact, decision);
        } catch (JobDeliveryException inadmissible) {
            throw new Refused(INADMISSIBLE, true, inadmissible);
        }

        var completion = new PublicReviewCompletion();
        completion.setJobId(job.getId());
        completion.setWorkspaceId(workspaceId);
        completion.setContractVersion(ComposedReview.CONTRACT_VERSION);
        completion.setAdmissionAttempt(ObservationAdmissionService.citationAttempt(job));
        completion.setAdmissionDigest(admitted);
        completion.setAcceptedAttempt(identity.attempt());
        completion.setAcceptedWorkerId(identity.workerId());
        completion.setReviewDigest(reviewDigest);
        completion.setReview(normalized);
        completion.setAcceptedAt(Instant.now());
        completions.save(completion);
        // No writer continues a review whose review on the work is accepted, sent or not.
        bases.deleteForJob(workspaceId, job.getId());
        return new Accepted(job, reviewDigest, intent);
    }

    /** Only pull request and issue reviews have a review on the work; conversations and documents keep their lanes. */
    private static ArtifactKind publicWorkOf(AgentJob job) {
        if (job.getJobType() == AgentJobType.PULL_REQUEST_REVIEW) return ArtifactKinds.PULL_REQUEST;
        if (job.getJobType() == AgentJobType.ISSUE_REVIEW) return ArtifactKinds.ISSUE;
        throw new Refused(NO_PUBLIC_WORK, false);
    }

    /**
     * The refusal on the job, under the same ownership fence, so its terminal output does not carry the refused review
     * onto the work. A review accepted meanwhile stays accepted; a lost attempt records nothing.
     */
    private void recordRefusal(AdmissionIdentity identity, String reason) {
        try {
            transactions.executeWithoutResult(status -> {
                AgentJob job = admission.ownedJob(identity);
                if (completions.existsByWorkspaceIdAndJobId(identity.workspaceId(), job.getId())) return;
                ObjectNode metadata = job.getMetadata() instanceof ObjectNode object
                        ? object.deepCopy()
                        : JsonNodeFactory.instance.objectNode();
                metadata.putObject(REFUSAL_METADATA_KEY).putArray("reasons").add(reason);
                job.setMetadata(metadata);
                jobs.save(job);
            });
        } catch (ObservationAdmissionService.StaleAttemptException lost) {
            // Not this attempt's job any more: its refusal is no fact about the job's review on the work.
        }
    }

    private record Accepted(
            AgentJob job, String reviewDigest, @Nullable FeedbackDispatch intent) {}

    /** What the submitting runner is told: accepted with the server's digest, or refused with fixed reasons. */
    public record Outcome(boolean accepted, @Nullable String reviewDigest, List<String> reasons) {
        static Outcome accepted(String reviewDigest) {
            return new Outcome(true, reviewDigest, List.of());
        }

        static Outcome refused(String reason) {
            return new Outcome(false, null, List.of(reason));
        }
    }

    /** A fixed refusal; {@code recorded} when it is a fact about the job's review on the work. */
    private static final class Refused extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        private final boolean recorded;

        Refused(String reason, boolean recorded) {
            this(reason, recorded, null);
        }

        Refused(String reason, boolean recorded, @Nullable Throwable cause) {
            super(reason, cause);
            this.recorded = recorded;
        }

        String reason() {
            return Objects.requireNonNull(getMessage());
        }

        boolean recorded() {
            return recorded;
        }
    }
}
