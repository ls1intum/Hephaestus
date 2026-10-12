package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.context.CitedSourceAccess;
import de.tum.cit.aet.hephaestus.agent.context.ReviewedWork;
import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.ValidatedProblem;
import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultParser.ValidatedResult;
import de.tum.cit.aet.hephaestus.agent.handler.ReviewResultTargetResolver.Target;
import de.tum.cit.aet.hephaestus.agent.handler.candidate.Candidate;
import de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateParser;
import de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateProblem;
import de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateRefusedException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ResultsRefusedException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ActorRole;
import de.tum.cit.aet.hephaestus.practices.PracticePreconditionClause;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeSignalOptions;
import de.tum.cit.aet.hephaestus.practices.ReviewRuleFingerprint;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.model.ResultOrigin;
import de.tum.cit.aet.hephaestus.practices.result.ObservationFingerprint;
import de.tum.cit.aet.hephaestus.practices.result.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.review.AutomatedReviewFence;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Admission of a review's results: every candidate is read with the contract's rules ({@link CandidateParser}), then
 * held to the trust checks of the run that produced it — the practice was admitted to the job and is not withdrawn,
 * the person it is about is the one the job names, every cited source was staged, and every quote is the exact text
 * at its place in the captured source. Each practice is admitted whole or withheld whole: one problem whose quote does
 * not verify withholds its practice, and a withheld negative never becomes a positive. A failure that impugns the run
 * rather than one claim refuses the whole submission.
 */
@Service
public class ReviewOutputService {

    private static final Logger log = LoggerFactory.getLogger(ReviewOutputService.class);

    /** The admission refusals that withhold one practice; every other refusal refuses the submission. */
    public static final String QUOTE_UNVERIFIED = "quote_unverified";

    public static final String DUTY_NOT_IN_CRITERIA = "duty_not_in_criteria";
    public static final String BASIS_STATE_UNSUPPORTED = "basis_state_unsupported";
    public static final String PRACTICE_WITHDRAWN = "practice_withdrawn";

    public static final String AMBIGUOUS_PRACTICE_RESULTS = "ambiguous_practice_results";
    public static final String PRACTICE_NOT_ADMITTED = "practice_not_admitted";
    public static final String UNAUTHORIZED_SOURCE = "unauthorized_source";
    public static final String WRONG_ATTRIBUTION = "wrong_attribution";
    public static final String NO_ADMISSIBLE_RESULTS = "no_admissible_results";

    /** Job metadata key holding the practices the admission withheld, with why; written with the admission. */
    public static final String WITHHELD_METADATA_KEY = "admission_withheld";

    private final PracticeRevisionRepository practiceRevisionRepository;
    private final PracticeResultRepository practiceResultRepository;
    private final ObservationRepository observationRepository;
    private final ReviewResultTargetResolver targetResolver;
    private final ObjectMapper objectMapper;
    private final CandidateEvidenceVerifier evidenceVerifier;
    private final CitedSourceAccess citedSourceAccess;
    private final ArtifactSourceCatalogRegistry sourceCatalogs;
    private final AutomatedReviewFence fence;
    private final LinkedIssueRepairAdmissionService repairAdmission;
    private final PracticeSignalOptions signalOptions;

    public ReviewOutputService(
            PracticeRevisionRepository practiceRevisionRepository,
            PracticeResultRepository practiceResultRepository,
            ObservationRepository observationRepository,
            ReviewResultTargetResolver targetResolver,
            ObjectMapper objectMapper,
            CandidateEvidenceVerifier evidenceVerifier,
            ArtifactSourceCatalogRegistry sourceCatalogs,
            AutomatedReviewFence fence,
            LinkedIssueRepairAdmissionService repairAdmission,
            PracticeSignalOptions signalOptions,
            CitedSourceAccess citedSourceAccess) {
        this.practiceRevisionRepository = practiceRevisionRepository;
        this.practiceResultRepository = practiceResultRepository;
        this.observationRepository = observationRepository;
        this.targetResolver = targetResolver;
        this.objectMapper = objectMapper;
        this.evidenceVerifier = evidenceVerifier;
        this.sourceCatalogs = sourceCatalogs;
        this.fence = fence;
        this.repairAdmission = repairAdmission;
        this.signalOptions = signalOptions;
        this.citedSourceAccess = citedSourceAccess;
    }

    /** Metadata key for the run's immutable result origin. */
    public static final String ORIGIN_METADATA_KEY = "observation_origin";

    /**
     * The origin stamped on this job, or {@link ResultOrigin#LIVE} for a job with no origin key: every such job came
     * from the event-driven path, so LIVE is a fact, not a guess.
     */
    public static ResultOrigin originOf(@Nullable JsonNode metadata) {
        JsonNode node = metadata == null ? null : metadata.get(ORIGIN_METADATA_KEY);
        if (node == null || !node.isString()) {
            return ResultOrigin.LIVE;
        }
        try {
            return ResultOrigin.valueOf(node.asString());
        } catch (IllegalArgumentException unknown) {
            // Reject unknown origins rather than classifying them as unbiased live runs.
            throw new JobDeliveryException("Unknown result origin in job metadata: " + node.asString(), unknown);
        }
    }

    @Transactional(readOnly = true)
    public void requirePublished(AgentJob job) {
        List<PracticeResult> results = practiceResultRepository.findByAgentJobId(
                job.getId(), job.getWorkspace().getId());
        Map<UUID, List<Observation>> problems = problemsOf(job.getWorkspace().getId(), results);
        for (PracticeResult result : results) {
            CitationVerification.requireVerifiedResult(job, result, problems.getOrDefault(result.getId(), List.of()));
        }
    }

    /** The problems of these results, by result id, in display order. */
    public Map<UUID, List<Observation>> problemsOf(Long workspaceId, List<PracticeResult> results) {
        if (results.isEmpty()) return Map.of();
        Map<UUID, List<Observation>> byResult = new LinkedHashMap<>();
        for (Observation problem : observationRepository.findByPracticeResultIds(
                workspaceId, results.stream().map(PracticeResult::getId).toList())) {
            byResult.computeIfAbsent(problem.getPracticeResultId(), id -> new ArrayList<>())
                    .add(problem);
        }
        return byResult;
    }

    /** The results this job recorded, each with its problems and the identities it was stored under. */
    @Transactional(readOnly = true)
    public List<ValidatedResult> recorded(AgentJob job, List<PracticeResult> persisted) {
        Map<UUID, List<Observation>> problems = problemsOf(job.getWorkspace().getId(), persisted);
        return persisted.stream()
                .map(result -> ValidatedResult.of(result, problems.getOrDefault(result.getId(), List.of())))
                .toList();
    }

    /** One practice's candidate as the runner submitted it, its practice named by the runner. */
    public record SubmittedResult(String practiceSlug, JsonNode candidate) {}

    /**
     * Reads the submitted results: one per practice, each a candidate. A repeated practice refuses the whole
     * submission.
     */
    public static List<SubmittedResult> submitted(JsonNode results) {
        if (!results.isArray() || results.isEmpty()) {
            throw new ResultsRefusedException(NO_ADMISSIBLE_RESULTS, "The review submitted no result");
        }
        List<SubmittedResult> submitted = new ArrayList<>(results.size());
        Set<String> practices = new HashSet<>();
        for (JsonNode entry : results) {
            String slug = entry.path("practiceSlug").asString("");
            if (!practices.add(slug)) {
                throw new ResultsRefusedException(
                        AMBIGUOUS_PRACTICE_RESULTS,
                        "A review submits one result for each practice. This practice repeats: " + slug);
            }
            submitted.add(new SubmittedResult(slug, entry.path("candidate")));
        }
        return List.copyOf(submitted);
    }

    /** One practice's candidate that passed every check, its citations annotated with their verification. */
    record AdmittedCandidate(String practiceSlug, Candidate candidate) {}

    /** A practice the admission withheld, with the reason and the citation that failed, if one did. */
    record Withheld(
            String practiceSlug,
            String reasonCode,
            String reason,
            @Nullable ObjectNode failure) {}

    public PreparedResults prepare(AgentJob job, JsonNode results) {
        List<SubmittedResult> submitted = submitted(results);
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (SubmittedResult entry : submitted) {
            try {
                candidates.put(entry.practiceSlug(), CandidateParser.parse(entry.candidate()));
            } catch (CandidateRefusedException refused) {
                throw new ResultsRefusedException(
                        refused.reasonCode(),
                        "The candidate of " + entry.practiceSlug() + " breaks the contract: " + refused.getMessage(),
                        refused);
            }
        }
        JsonNode metadata = job.getMetadata();
        if (metadata == null) {
            throw new JobDeliveryException("Missing job metadata: jobId=" + job.getId());
        }

        Admissible admissible = requireAdmissible(job, metadata);
        CapturedEvidence captured = admissible.evidence();
        Map<String, PracticeRevision> revisionsBySlug = admissible.revisionsBySlug();
        candidates.forEach((slug, candidate) -> {
            PracticeRevision revision = revisionsBySlug.get(slug);
            if (revision == null) {
                throw new ResultsRefusedException(
                        PRACTICE_NOT_ADMITTED, "A result names a practice not admitted to this review: " + slug);
            }
            enforceAttribution(slug, revision, job);
        });
        var codeQuotes = evidenceVerifier.verifyCodeQuotes(job, candidates.values(), captured);
        List<AdmittedCandidate> admitted = new ArrayList<>(candidates.size());
        List<Withheld> withheld = new ArrayList<>();
        boolean changeReaderAdmitted = false;
        for (var entry : candidates.entrySet()) {
            String slug = entry.getKey();
            Candidate candidate = entry.getValue();
            PracticeRevision revision = Objects.requireNonNull(revisionsBySlug.get(slug));
            if (fence.withdrawal(revision.getPractice()).isPresent()) {
                // A run prepared before its practice was withdrawn from automated review still returns what it found;
                // the withdrawal, not the run, decides whether that stands.
                withheld.add(new Withheld(slug, PRACTICE_WITHDRAWN, "Withdrawn from automated review", null));
                continue;
            }
            try {
                evidenceVerifier.verifyCandidate(slug, candidate, revision, captured, job, codeQuotes);
                admitted.add(new AdmittedCandidate(slug, candidate));
                changeReaderAdmitted |= Objects.requireNonNull(revision.getEvidenceRequirements()).stream()
                        .anyMatch(need -> PracticePreconditionClause.DIFF_SOURCE.equals(need.sourceKind()));
            } catch (CandidateEvidenceVerifier.Unverified unverified) {
                withheld.add(new Withheld(
                        slug, unverified.reasonCode(), String.valueOf(unverified.getMessage()), unverified.failure()));
            }
        }
        requireTheChangeRead(
                job.getId(),
                admitted,
                changeReaderAdmitted && captured.availableSources().contains(PracticePreconditionClause.DIFF_SOURCE));
        if (!withheld.isEmpty()) {
            log.warn(
                    "Withheld {} of {} result(s), admitting the rest: jobId={} withheld={}",
                    withheld.size(),
                    candidates.size(),
                    job.getId(),
                    withheld.stream()
                            .map(w -> w.practiceSlug() + ":" + w.reasonCode())
                            .toList());
            // Withholding the only fault leaves an all-clear standing over a defect the review did find, which is a
            // different statement to the reader than an incomplete review.
            boolean withheldNegative = withheld.stream()
                    .anyMatch(w -> Objects.requireNonNull(candidates.get(w.practiceSlug()))
                                    .outcome()
                            == Outcome.NOT_MET);
            if (withheldNegative
                    && admitted.stream().noneMatch(a -> a.candidate().outcome() == Outcome.NOT_MET)) {
                log.error(
                        "Withheld every negative result; the remaining claims read as an all-clear: jobId={}",
                        job.getId());
            }
        }
        ArrayNode withheldNode = withheldNode(withheld);
        if (admitted.isEmpty()) {
            throw new ResultsRefusedException(
                    NO_ADMISSIBLE_RESULTS,
                    "No result survived the evidence check, so there is nothing to record: jobId=" + job.getId()
                            + ", withheld="
                            + withheld.stream()
                                    .map(w -> w.practiceSlug() + ":" + w.reasonCode())
                                    .toList(),
                    failuresOf(withheldNode));
        }
        return new PreparedResults(
                job.getId(),
                job.getRetryCount(),
                job.getWorkerId(),
                job.getEvidenceSnapshot() == null
                        ? null
                        : job.getEvidenceSnapshot().deepCopy(),
                metadata.deepCopy(),
                List.copyOf(admitted),
                withheldNode);
    }

    private ArrayNode withheldNode(List<Withheld> withheld) {
        ArrayNode node = objectMapper.createArrayNode();
        for (Withheld entry : withheld) {
            ObjectNode out = node.addObject();
            out.put("practiceSlug", entry.practiceSlug());
            out.put("reasonCode", entry.reasonCode());
            out.put("reason", entry.reason());
            ArrayNode failures = out.putArray("verificationFailures");
            if (entry.failure() != null) failures.add(entry.failure());
        }
        return node;
    }

    /** The failed citations of the withheld practices, each naming its practice. */
    private ArrayNode failuresOf(ArrayNode withheld) {
        ArrayNode failures = objectMapper.createArrayNode();
        for (JsonNode entry : withheld) {
            for (JsonNode failure : entry.path("verificationFailures")) {
                if (!(failure instanceof ObjectNode object)) continue;
                ObjectNode copy = object.deepCopy();
                copy.put("practiceSlug", entry.path("practiceSlug").asString());
                failures.add(copy);
            }
        }
        return failures;
    }

    @Transactional
    public RecordedResults publish(AgentJob job, PreparedResults prepared) {
        if (!prepared.jobId.equals(job.getId())
                || prepared.attempt != job.getRetryCount()
                || !Objects.equals(prepared.workerId, job.getWorkerId())
                || !Objects.equals(prepared.snapshot, job.getEvidenceSnapshot())
                || !Objects.equals(prepared.metadata, job.getMetadata())) {
            throw new ResultAdmissionService.StaleAttemptException();
        }
        Long workspaceId = job.getWorkspace().getId();
        JsonNode metadata = Objects.requireNonNull(job.getMetadata());
        // Asked again under the ownership fence: what was admissible when the submission was verified
        // may have been withdrawn, erased or reassigned since.
        Admissible admissible = requireAdmissible(job, metadata);
        Target target = admissible.target();
        if (target.type().equals(ArtifactKinds.ISSUE)) {
            String admittedSnapshot = metadata.path("review_snapshot_id").asString("");
            UUID currentSnapshot = practiceResultRepository
                    .lockIssueSnapshotForReview(workspaceId, job.getId(), target.id())
                    .orElse(null);
            if (currentSnapshot == null || !currentSnapshot.toString().equals(admittedSnapshot)) {
                throw new JobDeliveryException("Issue changed after this review was submitted: jobId=" + job.getId());
            }
        }
        String signal =
                metadata.path(PracticeCatalogInjector.SIGNAL_METADATA_KEY).asString();
        boolean linkedRepair = !signal.isBlank() && signalOptions.isInternalRepair(SignalName.of(signal));
        if (linkedRepair) {
            repairAdmission.requireCurrentCapture(
                    job, target.id(), metadata.path("linked_issue_revision").asString());
        }
        Map<String, PracticeRevision> revisionsBySlug = admissible.revisionsBySlug();
        for (AdmittedCandidate admitted : prepared.admitted) {
            PracticeRevision revision = revisionsBySlug.get(admitted.practiceSlug());
            if (revision == null) throw new JobDeliveryException("Practice is no longer admitted to this review");
            enforceAttribution(admitted.practiceSlug(), revision, job);
            requireVerified(job, admitted.candidate());
        }
        if (metadata instanceof ObjectNode object) {
            object.set("citation_verification_failures", failuresOf(prepared.withheld));
            object.set(WITHHELD_METADATA_KEY, prepared.withheld.deepCopy());
        }
        ResultOrigin origin = originOf(metadata);
        // The one person this job resolved. Sound for every result only because the catalogue injector withheld every
        // practice whose occasion is about somebody else, and enforceAttribution above refuses one that reached here.
        Long aboutUserId = target.aboutUserId();
        ArtifactKind artifactKind = target.type();
        Long artifactId = target.id();
        String capturedRevision = capturedRevision(job);
        Map<Long, List<PracticeResult>> previousNegatives = linkedRepair
                ? repairAdmission.currentNegatives(workspaceId, artifactKind, artifactId, aboutUserId)
                : Map.of();
        Set<Long> replacedPractices = new HashSet<>();

        int inserted = 0;
        int discardedDuplicate = 0;
        Instant observedAt = Instant.now();
        List<ValidatedResult> recorded = new ArrayList<>(prepared.admitted.size());

        for (AdmittedCandidate admitted : prepared.admitted) {
            Candidate candidate = admitted.candidate();
            String slug = admitted.practiceSlug();
            PracticeRevision revision =
                    Objects.requireNonNull(revisionsBySlug.get(slug), "Validated practice revision is missing");
            Practice practice = revision.getPractice();
            Long practiceRevisionId = Objects.requireNonNull(revision.getId(), "Practice revision must be persisted");
            String occurrenceKey = slug + ":" + artifactKind.value() + ":" + artifactId + ":" + job.getId();
            String scopeKey = PracticeScriptInputs.scopeKey(
                    job, artifactKind, artifactId, Objects.requireNonNull(revision.getSubject()));
            ObjectNode evidence = evidenceOf(candidate);
            ObjectNode provenance = provenance(job, revision, slug, capturedRevision);
            List<ValidatedProblem> problems = new ArrayList<>();
            for (int position = 0; position < candidate.problems().size(); position++) {
                CandidateProblem problem = candidate.problems().get(position);
                ArrayNode citations = objectMapper.createArrayNode();
                problem.citations().forEach(citations::add);
                problems.add(new ValidatedProblem(
                        null,
                        position,
                        problem.key(),
                        problem.severity(),
                        problem.summary(),
                        problem.explanation(),
                        citations,
                        problem.basis(),
                        problem.nextStep(),
                        ObservationFingerprint.compute(
                                slug, artifactKind.value(), artifactId, aboutUserId, firstLocationPath(citations))));
            }

            UUID resultId = UUID.randomUUID();
            ObjectNode example = candidate.example();
            Outcome outcome = candidate.outcome();
            int rows = practiceResultRepository.insertIfAbsent(
                    resultId,
                    occurrenceKey,
                    job.getId(),
                    workspaceId,
                    practice.getId(),
                    practiceRevisionId,
                    artifactKind.value(),
                    artifactId,
                    aboutUserId,
                    candidate.summary(),
                    outcome.name(),
                    candidate.severity() == null ? null : candidate.severity().name(),
                    objectMapper.writeValueAsString(evidence),
                    candidate.explanation(),
                    scopeKey,
                    example == null ? null : objectMapper.writeValueAsString(example),
                    objectMapper.writeValueAsString(candidate.coverage()),
                    objectMapper.writeValueAsString(provenance),
                    observedAt,
                    origin.name());

            List<ValidatedProblem> stored = new ArrayList<>(problems.size());
            if (rows == 1) {
                inserted++;
                for (ValidatedProblem problem : problems) {
                    UUID observationId = UUID.randomUUID();
                    JsonNode basis = problem.basis();
                    observationRepository.insert(
                            observationId,
                            workspaceId,
                            resultId,
                            problem.position(),
                            problem.observationKey(),
                            problem.severity().name(),
                            problem.summary(),
                            problem.explanation(),
                            objectMapper.writeValueAsString(problem.citations()),
                            basis == null ? null : objectMapper.writeValueAsString(basis),
                            problem.nextStep(),
                            problem.recurrenceKey());
                    stored.add(problem.withId(observationId));
                }
                if (linkedRepair && outcome == Outcome.MET && previousNegatives.containsKey(practice.getId())) {
                    replacedPractices.add(practice.getId());
                }
            } else {
                PracticeResult existing = practiceResultRepository.findByAgentJobId(job.getId(), workspaceId).stream()
                        .filter(row -> row.getOccurrenceKey().equals(occurrenceKey))
                        .findFirst()
                        .orElseThrow(() -> new JobDeliveryException("Recorded result is unavailable for retry"));
                resultId = existing.getId();
                List<Observation> existingProblems =
                        problemsOf(workspaceId, List.of(existing)).getOrDefault(resultId, List.of());
                boolean same = sameRecord(
                        existing,
                        existingProblems,
                        candidate,
                        problems,
                        practice,
                        practiceRevisionId,
                        new Target(artifactKind, artifactId, aboutUserId),
                        evidence,
                        scopeKey,
                        provenance,
                        origin);
                if (!same) {
                    throw new JobDeliveryException("A recorded result cannot be changed by retry: slug=" + slug);
                }
                existingProblems.forEach(problem -> stored.add(ValidatedProblem.of(problem)));
                discardedDuplicate++;
            }
            recorded.add(new ValidatedResult(
                    slug,
                    candidate.summary(),
                    outcome,
                    candidate.severity(),
                    evidence,
                    candidate.explanation(),
                    new ResultKeys(occurrenceKey, resultId),
                    revision.getDeliveryBehavior(),
                    stored,
                    example));
        }

        for (Long practiceId : replacedPractices) {
            repairAdmission.retire(
                    workspaceId,
                    target,
                    practiceId,
                    Objects.requireNonNull(previousNegatives.get(practiceId)),
                    observedAt);
        }

        log.info(
                "Recorded this review's results: inserted={}, duplicate={}, jobId={}",
                inserted,
                discardedDuplicate,
                job.getId());

        return new RecordedResults(inserted, discardedDuplicate, recorded);
    }

    private static boolean sameRecord(
            PracticeResult stored,
            List<Observation> storedProblems,
            Candidate candidate,
            List<ValidatedProblem> problems,
            Practice practice,
            Long practiceRevisionId,
            Target target,
            JsonNode evidence,
            String scopeKey,
            JsonNode provenance,
            ResultOrigin origin) {
        PracticeRevision storedRevision = stored.getPracticeRevision();
        boolean same = Objects.equals(stored.getPractice().getId(), practice.getId())
                && storedRevision != null
                && Objects.equals(storedRevision.getId(), practiceRevisionId)
                && stored.getArtifactKind().equals(target.type())
                && Objects.equals(stored.getArtifactId(), target.id())
                && Objects.equals(stored.getAboutUserId(), target.aboutUserId())
                && stored.getSummary().equals(candidate.summary())
                && stored.getOutcome() == candidate.outcome()
                && stored.getSeverity() == candidate.severity()
                && sameJson(stored.getEvidence(), evidence)
                && Objects.equals(stored.getEvidenceRationale(), candidate.explanation())
                && sameJson(stored.getExample(), candidate.example())
                && sameJson(stored.getCoverage(), candidate.coverage())
                && sameJson(stored.getProvenance(), provenance)
                && stored.getScopeKey().equals(scopeKey)
                && stored.getOrigin() == origin
                && storedProblems.size() == problems.size();
        for (int index = 0; same && index < problems.size(); index++) {
            Observation was = storedProblems.get(index);
            ValidatedProblem now = problems.get(index);
            same = was.getPosition() == now.position()
                    && Objects.equals(was.getObservationKey(), now.observationKey())
                    && was.getSeverity() == now.severity()
                    && was.getSummary().equals(now.summary())
                    && was.getExplanation().equals(now.explanation())
                    && sameJson(was.getCitations(), now.citations())
                    && sameJson(was.getBasis(), now.basis())
                    && Objects.equals(was.getNextStep(), now.nextStep())
                    && Objects.equals(was.getRecurrenceKey(), now.recurrenceKey());
        }
        return same;
    }

    /**
     * JSON equality as a jsonb round trip keeps it: a {@code long} written by the server reads back as an
     * {@code int} node, and Jackson's node equality tells the two apart.
     */
    private static boolean sameJson(@Nullable JsonNode stored, @Nullable JsonNode current) {
        if (stored == null || current == null) return stored == null && current == null;
        return stored.equals(JSON_VALUE_ORDER, current);
    }

    private static final Comparator<JsonNode> JSON_VALUE_ORDER = (left, right) -> left.isNumber() && right.isNumber()
            ? left.decimalValue().compareTo(right.decimalValue())
            : left.equals(right) ? 0 : 1;

    /**
     * The result's evidence as stored: every citation it rests on — its own, then each problem's that is not already
     * among them — and the warrants its outcome carries. Every reader that authorizes, attributes or erases a result's
     * sources reads one list, as for a result recorded before problems were split out; a problem keeps its own
     * citations beside it, and the result's own are this list without its problems' ({@code ResultCitations}).
     */
    private ObjectNode evidenceOf(Candidate candidate) {
        ObjectNode evidence = objectMapper.createObjectNode();
        ArrayNode citations = evidence.putArray("citations");
        candidate.citations().forEach(citations::add);
        for (CandidateProblem problem : candidate.problems()) {
            for (ObjectNode citation : problem.citations()) {
                if (!citations.valueStream().anyMatch(citation::equals)) citations.add(citation);
            }
        }
        ObjectNode search = candidate.search();
        if (search != null) evidence.set("search", search);
        ObjectNode inapplicability = candidate.inapplicability();
        if (inapplicability != null) evidence.set("inapplicability", inapplicability);
        if (!candidate.openQuestions().isEmpty()) {
            ArrayNode questions = evidence.putArray("undecidability");
            candidate.openQuestions().forEach(questions::add);
        }
        return evidence;
    }

    /**
     * Where the result came from, from server-pinned data only: the job and attempt, the practice revision and the
     * program it holds, the revision the capture pinned, and the execution run that reports the program.
     */
    private ObjectNode provenance(
            AgentJob job, PracticeRevision revision, String practiceSlug, @Nullable String capturedRevision) {
        ObjectNode provenance = objectMapper.createObjectNode();
        provenance.put("jobId", job.getId().toString());
        provenance.put("attempt", job.getRetryCount());
        provenance.put("practiceRevisionId", revision.getId());
        ObjectNode program = provenance.putObject("program");
        String script = revision.getPracticeScript();
        if (script == null || script.isBlank()) {
            program.put("kind", "builtin");
            program.putNull("digest");
        } else {
            program.put("kind", "custom");
            program.put("digest", programDigest(script));
        }
        if (capturedRevision == null) provenance.putNull("capturedRevision");
        else provenance.put("capturedRevision", capturedRevision);
        ObjectNode run = provenance.putObject("executionRun");
        run.put("jobId", job.getId().toString());
        run.put("attempt", job.getRetryCount());
        run.put("practiceSlug", practiceSlug);
        return provenance;
    }

    /** The SHA-256 of a program as staged: the script's UTF-8 bytes. */
    public static String programDigest(String script) {
        return ProvenanceDigest.sha256Hex(script.getBytes(StandardCharsets.UTF_8));
    }

    /** The head the capture pinned, or null for a review that captured no repository head. */
    private static @Nullable String capturedRevision(AgentJob job) {
        JsonNode snapshot = job.getEvidenceSnapshot();
        JsonNode head = snapshot == null
                ? null
                : snapshot.path(ReviewedWork.SNAPSHOT_KEY).path("head");
        return head != null && head.isString() && head.asString().matches(CitationVerification.GIT_OBJECT_ID)
                ? head.asString()
                : null;
    }

    private static void requireVerified(AgentJob job, Candidate candidate) {
        ArrayNode citations = JsonNodeFactory.instance.arrayNode();
        CandidateEvidenceVerifier.located(candidate).forEach(cited -> citations.add(cited.citation()));
        CitationVerification.requireVerifiedCitations(job.getId(), job.getRetryCount(), citations);
    }

    /**
     * Refuses a review whose admitted results decided nothing when an admitted practice's pinned revision reads a
     * captured change and none of them read it. A review of comments or history alone owes the change nothing; a
     * withheld result neither burdens nor witnesses.
     */
    private static void requireTheChangeRead(UUID jobId, List<AdmittedCandidate> admitted, boolean changeOwed) {
        if (changeOwed
                && admitted.stream().noneMatch(a -> a.candidate().outcome().isDecided())
                && !readTheDiff(admitted)) {
            throw new ResultsRefusedException(
                    "did_not_read_the_diff",
                    "No result decided anything or quoted the change, and a change was captured."
                            + " The review answered without reading the change, so the server does not record it."
                            + " jobId=" + jobId);
        }
    }

    /**
     * Whether a result cites the diff or names it in a consulted-source warrant. This prevents a refusal; it does not
     * replace citation verification at admission.
     */
    private static boolean readTheDiff(List<AdmittedCandidate> admitted) {
        String diff = PracticePreconditionClause.DIFF_SOURCE.value();
        for (AdmittedCandidate entry : admitted) {
            Candidate candidate = entry.candidate();
            for (CandidateEvidenceVerifier.Located cited : CandidateEvidenceVerifier.located(candidate)) {
                if (diff.equals(cited.citation().path("sourceKind").asString())) return true;
            }
            // Metadata-only practices can report consulting the diff without quoting it.
            List<ObjectNode> warrants = new ArrayList<>();
            if (candidate.search() != null) warrants.add(candidate.search());
            if (candidate.inapplicability() != null) warrants.add(candidate.inapplicability());
            for (ObjectNode warrant : warrants) {
                for (JsonNode consulted : warrant.path("consulted")) {
                    if (diff.equals(consulted.asString())) return true;
                }
            }
        }
        return false;
    }

    /** What one admission may record against: the capture, the person and work it names, and its practices. */
    private record Admissible(
            CapturedEvidence evidence, Target target, Map<String, PracticeRevision> revisionsBySlug) {}

    private Admissible requireAdmissible(AgentJob job, JsonNode metadata) {
        if (!citedSourceAccess.permitsReviewResult(job))
            throw new ResultsRefusedException(
                    "member_ai_declined",
                    "The AI choice of the developer no longer permits the server to record this review result.");
        CapturedEvidence evidence = CapturedEvidence.of(job, objectMapper);
        for (SourceKind kind : evidence.availableSources()) {
            if (!sourceCatalogs.isSourceUsePermitted(
                    evidence.contractVersion(), kind, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY)) {
                throw new JobDeliveryException(
                        "Evidence source authorization was withdrawn before delivery: source=" + kind
                                + ", jobId="
                                + job.getId());
            }
        }
        Target target = targetResolver.resolve(job, metadata);
        return new Admissible(
                evidence, target, admittedRevisions(job, job.getWorkspace().getId()));
    }

    public static final class PreparedResults {
        private final UUID jobId;
        private final int attempt;
        private final @Nullable String workerId;
        private final @Nullable JsonNode snapshot;
        private final JsonNode metadata;
        private final List<AdmittedCandidate> admitted;
        private final ArrayNode withheld;

        private PreparedResults(
                UUID jobId,
                int attempt,
                @Nullable String workerId,
                @Nullable JsonNode snapshot,
                JsonNode metadata,
                List<AdmittedCandidate> admitted,
                ArrayNode withheld) {
            this.jobId = jobId;
            this.attempt = attempt;
            this.workerId = workerId;
            this.snapshot = snapshot;
            this.metadata = metadata;
            this.admitted = admitted;
            this.withheld = withheld;
        }

        /** The practices the admission withheld, each with its reason and failed citation. */
        public ArrayNode withheld() {
            return withheld.deepCopy();
        }
    }

    private static void enforceAttribution(String practiceSlug, PracticeRevision revision, AgentJob job) {
        ActorRole subject = revision.getSubject();
        if (subject == null) {
            throw new JobDeliveryException("Practice revision has no attribution definition: slug=" + practiceSlug);
        }
        if (PracticeCatalogInjector.subjectNameable(subject, job.getMetadata())) {
            return;
        }
        throw new ResultsRefusedException(
                WRONG_ATTRIBUTION,
                "A result is about a " + subject
                        + " this review cannot name, so it has nobody to be filed against: slug="
                        + practiceSlug
                        + ", jobId="
                        + job.getId());
    }

    private Map<String, PracticeRevision> admittedRevisions(AgentJob job, Long workspaceId) {
        JsonNode practices = requireEvidenceSnapshot(job).path("practices");
        if (!practices.isArray() || practices.isEmpty()) {
            throw new JobDeliveryException("Job evidence snapshot has no admitted practices: jobId=" + job.getId());
        }
        Map<String, PracticeRevision> admitted = new HashMap<>();
        for (JsonNode entry : practices) {
            String slug = entry.path("slug").asString();
            JsonNode revisionId = entry.path("revisionId");
            if (slug.isBlank() || !revisionId.isIntegralNumber()) {
                throw new JobDeliveryException("Job evidence snapshot has an invalid practice: jobId=" + job.getId());
            }
            PracticeRevision revision = practiceRevisionRepository
                    .findByIdAndWorkspaceId(revisionId.asLong(), workspaceId)
                    .orElseThrow(() -> new JobDeliveryException(
                            "Admitted practice revision no longer exists: jobId=" + job.getId()));
            if (!ReviewRuleFingerprint.isCurrentScheme(revision.getReviewRuleFingerprint())) {
                throw new JobDeliveryException(
                        "Admitted practice revision uses an incompatible review standard: jobId=" + job.getId());
            }
            Practice practice = revision.getPractice();
            if (!slug.equals(revision.getSlug())
                    || !workspaceId.equals(practice.getWorkspace().getId())) {
                throw new JobDeliveryException(
                        "Admitted practice revision does not match the job: jobId=" + job.getId());
            }
            if (admitted.put(slug, revision) != null) {
                throw new JobDeliveryException("Duplicate admitted practice slug: " + slug + ", jobId=" + job.getId());
            }
        }
        return admitted;
    }

    private static JsonNode requireEvidenceSnapshot(AgentJob job) {
        JsonNode snapshot = job.getEvidenceSnapshot();
        if (snapshot == null || !snapshot.isObject()) {
            throw new JobDeliveryException("Job has no evidence snapshot: jobId=" + job.getId());
        }
        return snapshot;
    }

    /** Checked against executable review kinds by {@link JobTypeReviewExecutionCatalog} at startup. */
    static final Set<ArtifactKind> ROUTABLE_KINDS = Set.of(
            ArtifactKinds.PULL_REQUEST, ArtifactKinds.ISSUE, ArtifactKinds.CONVERSATION_THREAD, ArtifactKinds.DOCUMENT);

    /** The path of the first citation, which locates a problem for its recurrence key. */
    static @Nullable String firstLocationPath(@Nullable JsonNode citations) {
        if (citations == null || !citations.isArray() || citations.isEmpty()) {
            return null;
        }
        JsonNode first = citations.get(0);
        if (first == null || !first.isObject()) {
            return null;
        }
        JsonNode path = first.get("path");
        return path != null && path.isString() ? path.asString() : null;
    }

    /** @param recorded what this call persisted, each carrying the identities it was stored under. */
    public record RecordedResults(int inserted, int discardedDuplicate, List<ValidatedResult> recorded) {}
}
