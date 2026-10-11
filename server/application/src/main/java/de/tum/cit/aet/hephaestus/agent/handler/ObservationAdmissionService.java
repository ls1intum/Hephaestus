package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.providers.ReviewHistoryContentSource;
import de.tum.cit.aet.hephaestus.agent.handler.conversation.ConversationEligibility;
import de.tum.cit.aet.hephaestus.agent.handler.conversation.ConversationalDeliveryListener;
import de.tum.cit.aet.hephaestus.agent.handler.inapp.InAppSupportContext;
import de.tum.cit.aet.hephaestus.agent.handler.inapp.InAppSupportReader;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ObservationsRefusedException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedObservations;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.agent.job.ReviewCompositionBasis;
import de.tum.cit.aet.hephaestus.agent.job.ReviewCompositionBasisRepository;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.practices.PracticePreconditionClause;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import java.io.Serial;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ObservationAdmissionService {

    private static final Logger log = LoggerFactory.getLogger(ObservationAdmissionService.class);

    /** The response key of the IN_APP support read after admission; see {@link InAppSupportContext}. */
    public static final String IN_APP_SUPPORT_KEY = "inAppSupport";

    /** The response key of the IN_CHAT routing read after admission; see {@link ConversationEligibility}. */
    public static final String IN_CHAT_ELIGIBILITY_KEY = "inChatEligibility";

    public static final String DIGEST_METADATA_KEY = "observation_admission_digest";

    /** The attempt whose citations the admission verified; a later attempt of the same job only composes. */
    public static final String ATTEMPT_METADATA_KEY = "observation_admission_attempt";

    public static final String REFUSAL_METADATA_KEY = "observation_admission_refusal";

    public static final String INADMISSIBLE_REASON_CODE = "inadmissible_observations";

    /** Why an admitted job that lost its run is failed instead of measured again. */
    public static final String INTERRUPTED_AFTER_ADMISSION =
            "The review was interrupted after its observations were admitted. No completed feedback was saved. "
                    + "The observations are kept.";

    static boolean observationsWereRefused(AgentJob job) {
        return job.getMetadata() != null
                && !job.getMetadata()
                        .path(REFUSAL_METADATA_KEY)
                        .path("reasonCode")
                        .asString()
                        .isBlank();
    }

    static void requireMatchingCompositionDigest(AgentJob job) {
        String admitted = job.getMetadata() == null
                ? ""
                : job.getMetadata().path(DIGEST_METADATA_KEY).asString();
        String composed = job.getOutput() == null
                ? ""
                : job.getOutput().path("feedback").path("admissionDigest").asString();
        if (admitted.isBlank() || !admitted.equals(composed)) {
            throw new JobDeliveryException("Feedback was not composed from this job's admitted observations");
        }
    }

    private final AgentJobRepository jobs;
    private final ObservationRepository observations;
    private final JobTypeHandlerRegistry handlers;
    private final JsonMapper mapper;
    private final TransactionTemplate transactions;
    private final JobEvidenceFiles evidenceFiles;
    private final PublicReviewEligibility publicReviewEligibility;
    private final ReviewHistoryContentSource reviewHistory;
    private final ObjectProvider<InAppSupportReader> inAppSupportReader;
    private final ConversationalDeliveryListener conversationLane;
    private final PullRequestRepository pullRequests;
    private final ReviewCompositionBasisRepository basisRepository;

    /** Retries join in-flight verification rather than launching duplicate Git operations. */
    private final ConcurrentHashMap<AdmissionIdentity, Flight> flights = new ConcurrentHashMap<>();

    private record Flight(String digest, CompletableFuture<ObjectNode> outcome) {}

    public ObservationAdmissionService(
            AgentJobRepository jobs,
            ObservationRepository observations,
            JobTypeHandlerRegistry handlers,
            JsonMapper mapper,
            PlatformTransactionManager transactionManager,
            JobEvidenceFiles evidenceFiles,
            PublicReviewEligibility publicReviewEligibility,
            ReviewHistoryContentSource reviewHistory,
            ObjectProvider<InAppSupportReader> inAppSupportReader,
            ConversationalDeliveryListener conversationLane,
            PullRequestRepository pullRequests,
            ReviewCompositionBasisRepository basisRepository) {
        this.conversationLane = conversationLane;
        this.pullRequests = pullRequests;
        this.basisRepository = basisRepository;
        this.jobs = jobs;
        this.observations = observations;
        this.handlers = handlers;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
        this.evidenceFiles = evidenceFiles;
        this.publicReviewEligibility = publicReviewEligibility;
        this.reviewHistory = reviewHistory;
        this.inAppSupportReader = inAppSupportReader;
    }

    /** Records refusals on the job before rethrowing them. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ObjectNode admit(AdmissionIdentity identity, JsonNode submitted) {
        String digest = ProvenanceDigest.sha256Hex(serializedPayload(submitted));
        Flight mine = new Flight(digest, new CompletableFuture<>());
        Flight running = flights.putIfAbsent(identity, mine);
        if (running != null) {
            if (!running.digest().equals(digest)) throw new AdmissionConflictException();
            return join(running);
        }
        try {
            ObjectNode admitted = admitOnce(identity, submitted, digest);
            evidenceFiles.discardAdmittedAttempt(identity);
            // Read once the admission has committed, so a failed read cannot undo it. A joined request shares
            // this dated read; a later retry of the same submission reads again.
            admitted.set(IN_APP_SUPPORT_KEY, mapper.valueToTree(inAppSupport(identity)));
            admitted.set(IN_CHAT_ELIGIBILITY_KEY, mapper.valueToTree(inChatEligibility(identity)));
            mine.outcome().complete(admitted);
            return admitted;
        } catch (RuntimeException exception) {
            mine.outcome().completeExceptionally(exception);
            throw exception;
        } finally {
            flights.remove(identity, mine);
        }
    }

    /**
     * The IN_APP support of the admitted run, for its owning attempt. A lost attempt stays a lost attempt; any other
     * failure leaves the support unavailable and the admission as committed.
     */
    private InAppSupportContext inAppSupport(AdmissionIdentity identity) {
        try {
            return evidenceFiles.indexLateRead(
                    identity,
                    () -> Objects.requireNonNull(transactions.execute(status -> {
                        AgentJob job = ownedJob(identity);
                        InAppSupportReader reader = inAppSupportReader.getIfAvailable();
                        return reader == null
                                ? InAppSupportContext.unavailable(Instant.now())
                                : reader.snapshot(
                                        job, observations.findByAgentJobId(identity.jobId(), identity.workspaceId()));
                    })),
                    support -> new JobEvidenceFiles.LateCopies(
                            support.practices().stream()
                                    .flatMap(practice -> practice.occurrences().stream())
                                    .map(InAppSupportContext.Occurrence::observationId)
                                    .collect(Collectors.toSet()),
                            Set.of()));
        } catch (StaleAttemptException lost) {
            throw lost;
        } catch (RuntimeException e) {
            log.warn(
                    "IN_APP support could not be read after admission: jobId={}, error={}",
                    identity.jobId(),
                    e.getClass().getSimpleName());
            return InAppSupportContext.unavailable(Instant.now());
        }
    }

    /**
     * The IN_CHAT routing of the admitted run, for its owning attempt. It copies nothing from other reviews, so it is
     * not indexed. A lost attempt stays a lost attempt; any other failure leaves the routing unavailable.
     */
    private ConversationEligibility inChatEligibility(AdmissionIdentity identity) {
        try {
            return Objects.requireNonNull(transactions.execute(status -> conversationLane.eligibility(
                    ownedJob(identity), observations.findByAgentJobId(identity.jobId(), identity.workspaceId()))));
        } catch (StaleAttemptException lost) {
            throw lost;
        } catch (RuntimeException e) {
            log.warn(
                    "IN_CHAT routing could not be read after admission: jobId={}, error={}",
                    identity.jobId(),
                    e.getClass().getSimpleName());
            return ConversationEligibility.unavailable(Instant.now());
        }
    }

    /** A joined retry answers with the first attempt's own exception, not the join's wrapper. */
    @SuppressWarnings("PMD.PreserveStackTrace")
    private static ObjectNode join(Flight running) {
        try {
            return running.outcome().join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException cause) throw cause;
            throw exception;
        }
    }

    /**
     * The work a pull request review was admitted for, exactly as its job recorded it: the pull request row it locks
     * and the repository and number that row was for. Only the key of a job that names a positive id; it locks a row,
     * it authorizes nothing.
     */
    record ReviewedTarget(
            long workspaceId,
            AgentJobType jobType,
            @Nullable ArtifactKind artifactKind,
            @Nullable IntegrationKind integrationKind,
            long pullRequestId,
            @Nullable JsonNode repositoryId,
            @Nullable JsonNode repositoryFullName,
            @Nullable JsonNode pullRequestNumber) {

        static @Nullable ReviewedTarget of(AgentJob job) {
            JsonNode metadata = job.getMetadata();
            if (metadata == null || job.getJobType() != AgentJobType.PULL_REQUEST_REVIEW) {
                return null;
            }
            JsonNode id = metadata.get("pull_request_id");
            if (id == null || !id.isIntegralNumber() || !id.canConvertToLong() || id.asLong() <= 0) {
                return null;
            }
            return new ReviewedTarget(
                    job.getWorkspace().getId(),
                    job.getJobType(),
                    job.getArtifactKind(),
                    job.getIntegrationKind(),
                    id.asLong(),
                    metadata.get("repository_id"),
                    metadata.get("repository_full_name"),
                    metadata.get("pr_number"));
        }
    }

    private ObjectNode admitOnce(AdmissionIdentity identity, JsonNode submitted, String digest) {
        AgentJob candidate = Objects.requireNonNull(transactions.execute(status -> ownedJob(identity)));
        // An attempt that continues an admitted review only composes: it admits nothing, not even a replay.
        if (isContinuation(candidate)) throw new StaleAttemptException();
        String existing = admissionDigest(candidate);
        if (!existing.isBlank()) {
            return Objects.requireNonNull(transactions.execute(status -> {
                AgentJob job = ownedJob(identity);
                if (!digest.equals(admissionDigest(job))) throw new AdmissionConflictException();
                return response(job, digest, observations.findByAgentJobId(identity.jobId(), identity.workspaceId()));
            }));
        }
        ReviewedTarget target = ReviewedTarget.of(candidate);
        PreparedObservations prepared;
        try {
            prepared = handlers.getHandler(candidate.getJobType()).prepareObservations(candidate, submitted);
        } catch (ObservationsRefusedException refusal) {
            recordRefusal(identity, refusal.reasonCode(), refusal.reason(), refusal.verificationFailures());
            throw refusal;
        } catch (JobDeliveryException inadmissible) {
            recordRefusal(
                    identity,
                    INADMISSIBLE_REASON_CODE,
                    String.valueOf(inadmissible.getMessage()),
                    JsonNodeFactory.instance.arrayNode());
            throw inadmissible;
        }
        // Read from the attempt's own folder before any lock is taken, and kept only with the admission it belongs to.
        ReviewCompositionBasis basis = originalBasis(identity, digest);
        try {
            return Objects.requireNonNull(transactions.execute(status -> {
                // Copy admission first, as erasure orders it before work and job locks. The inputs read before it are
                // kept only while their receipt still vouches for them; an erasure after waits, then finds this job.
                boolean keep = basis != null && evidenceFiles.holdVouchingReceipt(identity, basis.getCopyId());
                // The work before the job, as every writer that orders against this admission takes them: a
                // reservation of public feedback, a mirror write, and the coalescer reading the latest running review.
                // The job is not held while waiting, so the key is the prepared candidate's, checked once it is.
                if (target != null) pullRequests.lockById(target.pullRequestId());
                AgentJob job = ownedJob(identity);
                if (!Objects.equals(target, ReviewedTarget.of(job))) throw new StaleAttemptException();
                String admitted = admissionDigest(job);
                if (!admitted.isBlank()) {
                    if (!digest.equals(admitted)) throw new AdmissionConflictException();
                } else {
                    prepared.record(job);
                    ObjectNode metadata = job.getMetadata() instanceof ObjectNode object
                            ? object.deepCopy()
                            : mapper.createObjectNode();
                    metadata.remove(REFUSAL_METADATA_KEY);
                    metadata.put(DIGEST_METADATA_KEY, digest);
                    metadata.put(ATTEMPT_METADATA_KEY, identity.attempt());
                    job.setMetadata(metadata);
                    jobs.save(job);
                    if (keep) {
                        basisRepository.save(Objects.requireNonNull(basis));
                    }
                    jobs.discardRetiredArtifactInventory(
                            job.getId(), identity.workspaceId(), identity.attempt(), identity.workerId());
                }
                return response(job, digest, observations.findByAgentJobId(identity.jobId(), identity.workspaceId()));
            }));
        } catch (ObservationsRefusedException refusal) {
            // The publish transaction rolled back before this separate refusal write.
            recordRefusal(identity, refusal.reasonCode(), refusal.reason(), refusal.verificationFailures());
            throw refusal;
        } catch (JobDeliveryException inadmissible) {
            recordRefusal(
                    identity,
                    INADMISSIBLE_REASON_CODE,
                    String.valueOf(inadmissible.getMessage()),
                    JsonNodeFactory.instance.arrayNode());
            throw inadmissible;
        }
    }

    /**
     * A separately dated communication read: it cannot roll back or replace the admitted assessment. Its rows come
     * from other reviews, so they are indexed in this attempt's receipt before the sandbox receives them.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ObjectNode publicFeedbackHistory(AdmissionIdentity identity) {
        return evidenceFiles.indexLateRead(
                identity, () -> readPublicFeedbackHistory(identity), ObservationAdmissionService::publicHistoryCopies);
    }

    private static JobEvidenceFiles.LateCopies publicHistoryCopies(ObjectNode read) {
        Set<UUID> feedback = new HashSet<>();
        Set<UUID> basedOn = new HashSet<>();
        read.path("history").path("feedback").forEach(row -> {
            feedback.add(UUID.fromString(row.path("id").asString()));
            row.path("basedOn")
                    .forEach(support ->
                            basedOn.add(UUID.fromString(support.path("id").asString())));
        });
        return new JobEvidenceFiles.LateCopies(basedOn, feedback);
    }

    private ObjectNode readPublicFeedbackHistory(AdmissionIdentity identity) {
        return Objects.requireNonNull(transactions.execute(status -> {
            AgentJob job = ownedJob(identity);
            if (admissionDigest(job).isBlank()) {
                throw new PublicHistoryRefusedException("This review's observations have not been admitted");
            }
            List<Observation> admitted = observations.findByAgentJobId(identity.jobId(), identity.workspaceId());
            Set<UUID> publicIds = publicReviewEligibility.publicObservationIds(job, admitted);
            Observation speaksToAuthor = admitted.stream()
                    .filter(observation -> publicIds.contains(observation.getId()))
                    .findFirst()
                    .orElseThrow(() -> new PublicHistoryRefusedException(
                            "No admitted observation may be spoken about on this work"));
            ObjectNode history = reviewHistory.publicSameWorkFeedback(
                    identity.workspaceId(),
                    speaksToAuthor.getAboutUserId(),
                    speaksToAuthor.getArtifactKind(),
                    speaksToAuthor.getArtifactId());
            ObjectNode root = mapper.createObjectNode();
            root.put("schemaVersion", 1);
            root.put("readAt", Instant.now().toString());
            root.set("history", history);
            return root;
        }));
    }

    /** The admitted observations as the admission answered them, for a writer that continues this review. */
    @Transactional(readOnly = true)
    public ObjectNode admittedResponse(AgentJob job) {
        return response(
                job,
                admissionDigest(job),
                observations.findByAgentJobId(job.getId(), job.getWorkspace().getId()));
    }

    public static boolean isAdmitted(AgentJob job) {
        return !admissionDigest(job).isBlank();
    }

    /**
     * The attempt the admitted citations were verified under. Before admission, and for a job admitted before the
     * attempt was recorded, that is the current attempt: such a job is never continued under another one.
     */
    public static int citationAttempt(AgentJob job) {
        JsonNode attempt = job.getMetadata() == null ? null : job.getMetadata().get(ATTEMPT_METADATA_KEY);
        if (!isAdmitted(job) || attempt == null) return job.getRetryCount();
        // Anything but an earlier or the current attempt was not written by the admission, and verifies nothing.
        if (!attempt.isIntegralNumber()
                || !attempt.canConvertToInt()
                || attempt.asInt() < 0
                || attempt.asInt() > job.getRetryCount()) return -1;
        return attempt.asInt();
    }

    /** Whether this attempt continues an admitted review whose citations an earlier attempt verified. */
    public static boolean isContinuation(AgentJob job) {
        int admitted = citationAttempt(job);
        return isAdmitted(job) && admitted >= 0 && admitted < job.getRetryCount();
    }

    /**
     * The original public writer inputs of the admitting attempt, ready to be kept with its admission: only when its
     * public lane was open, its capture receipt indexes every person the inputs copied, and every captured record
     * reads back with the bytes the capture recorded. Otherwise nothing is kept, and the review stays unrecoverable
     * if its writer is lost.
     */
    private @Nullable ReviewCompositionBasis originalBasis(AdmissionIdentity identity, String digest) {
        var receipt = evidenceFiles.completeReceipt(identity);
        if (receipt.isEmpty()) return null;
        var files = evidenceFiles.originalPublicInputs(identity).orElse(null);
        if (files == null) return null;
        byte[] request = files.get(SandboxLayout.FEEDBACK_COMPOSITION_PATH);
        if (request == null
                || !mapper.readTree(request)
                        .path("channels")
                        .path("IN_CONTEXT")
                        .path("enabled")
                        .asBoolean(false)) return null;
        ObjectNode payload = mapper.createObjectNode();
        ObjectNode stored = payload.putObject("files");
        files.forEach((path, bytes) -> stored.put(path, Base64.getEncoder().encodeToString(bytes)));
        Instant now = Instant.now();
        var basis = new ReviewCompositionBasis();
        basis.setJobId(identity.jobId());
        basis.setWorkspaceId(identity.workspaceId());
        basis.setAdmissionAttempt(identity.attempt());
        basis.setAdmissionDigest(digest);
        basis.setBasisVersion(ReviewCompositionBasis.VERSION);
        basis.setBasisDigest(ReviewCompositionBasis.digest(files));
        basis.setCopyId(receipt.get());
        basis.setByteSize(mapper.writeValueAsBytes(payload).length);
        basis.setCreatedAt(now);
        basis.setExpiresAt(now.plus(ReviewCompositionBasis.RETENTION));
        basis.setPayload(payload);
        return basis;
    }

    private static String admissionDigest(AgentJob job) {
        return job.getMetadata() == null
                ? ""
                : job.getMetadata().path(DIGEST_METADATA_KEY).asString();
    }

    /** Persists the refusal under the ownership fence before propagating the verification failure. */
    public void recordRefusal(
            AdmissionIdentity identity, String reasonCode, String reason, JsonNode verificationFailures) {
        transactions.executeWithoutResult(status -> {
            AgentJob job = ownedJob(identity);
            ObjectNode metadata =
                    job.getMetadata() instanceof ObjectNode object ? object.deepCopy() : mapper.createObjectNode();
            if (!metadata.path(DIGEST_METADATA_KEY).asString().isBlank()) {
                throw new AdmissionConflictException();
            }
            ObjectNode refusal = mapper.createObjectNode();
            refusal.put("reasonCode", reasonCode);
            refusal.put("reason", reason);
            metadata.set(REFUSAL_METADATA_KEY, refusal);
            metadata.set("citation_verification_failures", verificationFailures.deepCopy());
            job.setMetadata(metadata);
            jobs.save(job);
        });
    }

    private AgentJob ownedJob(AdmissionIdentity identity) {
        AgentJob job = jobs.findByIdWithWorkspaceForUpdate(identity.jobId()).orElseThrow(StaleAttemptException::new);
        if (job.getStatus() != AgentJobStatus.RUNNING
                || !job.getWorkspace().getId().equals(identity.workspaceId())
                || job.getRetryCount() != identity.attempt()
                || !identity.workerId().equals(job.getWorkerId())) {
            throw new StaleAttemptException();
        }
        return job;
    }

    public record AdmissionIdentity(UUID jobId, Long workspaceId, int attempt, String workerId) {}

    public static class StaleAttemptException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    private byte[] serializedPayload(JsonNode submitted) {
        try {
            return mapper.writeValueAsBytes(submitted);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid observation payload", e);
        }
    }

    private ObjectNode response(AgentJob job, String digest, List<Observation> admitted) {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("admissionDigest", digest);
        ArrayNode rows = root.putArray("observations");
        Set<UUID> publicIds = publicReviewEligibility.publicObservationIds(job, admitted);
        admitted.forEach(o -> rows.add(project(o, publicIds.contains(o.getId()))));
        return root;
    }

    private ObjectNode project(Observation observation, boolean publicEligible) {
        ObjectNode out = mapper.createObjectNode();
        out.put("id", observation.getId().toString());
        out.put("practiceSlug", observation.getPractice().getSlug());
        out.put("summary", observation.getSummary());

        out.put("outcome", observation.getOutcome().name());
        var revision = observation.getPracticeRevision();
        boolean summaryOnly = revision != null && revision.getDeliveryBehavior().summaryOnly();
        out.put("summaryOnly", summaryOnly);
        out.put(
                "severity",
                observation.getSeverity() == null
                        ? null
                        : observation.getSeverity().name());
        out.put("evidenceRationale", observation.getEvidenceRationale());
        out.set("evidence", observation.getEvidence());
        ArrayNode citations = out.putArray("citations");
        JsonNode source = observation.getEvidence() == null
                ? null
                : observation.getEvidence().path("citations");
        if (source != null && source.isArray()) {
            int index = 0;
            for (JsonNode citation : source) {
                ObjectNode copy = citations.addObject();
                copy.put("index", index++);
                citation.properties().forEach(entry -> copy.set(entry.getKey(), entry.getValue()));
                boolean anchorable = !summaryOnly
                        && PracticePreconditionClause.DIFF_SOURCE
                                .value()
                                .equals(citation.path("sourceKind").asString())
                        && citation.path("path").isString()
                        && citation.path("startLine").isIntegralNumber()
                        && "NEW".equals(citation.path("side").asString())
                        && "VERIFIED"
                                .equals(citation.path("verification")
                                        .path("status")
                                        .asString())
                        && "EXACT_LOCATION"
                                .equals(citation.path("verification")
                                        .path("scope")
                                        .asString());
                copy.put("anchorable", anchorable);
            }
        }
        out.put(
                "anchorable",
                citations.valueStream().anyMatch(c -> c.path("anchorable").asBoolean()));
        // The review on the work is composed from the observations marked here and admitted by the same rule.
        out.put("publicEligible", publicEligible);
        return out;
    }

    public static class PublicHistoryRefusedException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        public PublicHistoryRefusedException(String reason) {
            super(reason);
        }
    }

    public static class AdmissionConflictException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
