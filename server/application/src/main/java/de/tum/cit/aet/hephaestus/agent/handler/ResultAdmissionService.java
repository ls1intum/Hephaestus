package de.tum.cit.aet.hephaestus.agent.handler;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.providers.ReviewHistoryContentSource;
import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedResults;
import de.tum.cit.aet.hephaestus.agent.job.ReviewCompositionBasis;
import de.tum.cit.aet.hephaestus.agent.job.ReviewCompositionBasisRepository;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobAssignments;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.AdmissionRequestDTO;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.AdmissionResponseDTO;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.AdmittedProblemDTO;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.AdmittedResultDTO;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.PrepareFeedbackRequestDTO;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.PrepareFeedbackResponseDTO;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.ProjectedCitationDTO;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.PublicFeedbackHistoryDTO;
import de.tum.cit.aet.hephaestus.agent.runtime.ProvenanceDigest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackAnchor;
import de.tum.cit.aet.hephaestus.practices.spi.AdmittedResultQuery;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceCitation;
import de.tum.cit.aet.hephaestus.practices.spi.InapplicabilityWarrant;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewWriteGuard;
import de.tum.cit.aet.hephaestus.practices.spi.SearchWarrant;
import java.io.Serial;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Admission is fenced by workspace, review, job, worker and exact execution attempt. */
@Service
@ConditionalOnCaptureRole
public class ResultAdmissionService {
    public static final String INADMISSIBLE_REASON_CODE = "inadmissible_results";
    public static final String INTERRUPTED_AFTER_ADMISSION =
            "The review stopped after its results were admitted. The recorded results are kept.";
    private final ReviewJobRepository jobs;
    private final ReviewOutputService output;
    private final AdmittedResultQuery proof;
    private final ReviewJobAssignments assignments;
    private final PublicReviewEligibility publicEligibility;
    private final JobEvidenceFiles files;
    private final ReviewHistoryContentSource history;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    private final ReviewCompositionBasisRepository bases;
    private final FeedbackCompositionService composition;
    private final ReviewWriteGuard writeGuard;

    public ResultAdmissionService(
            ReviewJobRepository jobs,
            ReviewOutputService output,
            AdmittedResultQuery proof,
            ReviewJobAssignments assignments,
            PublicReviewEligibility publicEligibility,
            JobEvidenceFiles files,
            ReviewHistoryContentSource history,
            ObjectMapper mapper,
            PlatformTransactionManager manager,
            ReviewCompositionBasisRepository bases,
            FeedbackCompositionService composition,
            ReviewWriteGuard writeGuard) {
        this.jobs = jobs;
        this.output = output;
        this.proof = proof;
        this.assignments = assignments;
        this.publicEligibility = publicEligibility;
        this.files = files;
        this.history = history;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(manager);
        this.bases = bases;
        this.composition = composition;
        this.writeGuard = writeGuard;
    }

    public AdmissionResponseDTO admit(AdmissionIdentity identity, AdmissionRequestDTO request) {
        String digest = ProvenanceDigest.sha256Hex(mapper.writeValueAsBytes(request));
        ReviewJob candidate = Objects.requireNonNull(transactions.execute(status -> ownedJob(identity)));
        requireIdentity(candidate, request.reviewId(), request.jobId(), request.attempt());
        if (isContinuation(candidate)) throw new StaleAttemptException();
        PreparedResults prepared = isAdmitted(candidate) ? null : output.prepare(candidate, request);
        ReviewJob recorded = Objects.requireNonNull(transactions.execute(status -> {
            ReviewJob owned = ownedJob(identity);
            requireIdentity(owned, request.reviewId(), request.jobId(), request.attempt());
            if (isAdmitted(owned)) {
                if (!digest.equals(owned.getAdmissionDigest())) throw new AdmissionConflictException();
            } else {
                Objects.requireNonNull(prepared).record(owned);
                owned.setAdmissionDigest(digest);
                owned.setAdmissionAttempt(identity.attempt());
                jobs.saveAndFlush(owned);
                retainCompositionInputs(identity, owned, digest);
            }
            return owned;
        }));
        return admittedResponse(recorded);
    }

    private void retainCompositionInputs(AdmissionIdentity identity, ReviewJob job, String digest) {
        if (assignments.review(job).kind() != ReviewKind.ITEM) return;
        var copy = files.completeReceipt(identity).orElse(null);
        if (copy == null || !files.holdVouchingReceipt(identity, copy)) return;
        var original = files.originalPublicInputs(identity).orElse(null);
        if (original == null) return;
        var basis = new ReviewCompositionBasis();
        basis.setJobId(job.getId());
        basis.setWorkspaceId(job.getWorkspaceId());
        basis.setAdmissionAttempt(job.getAttempt());
        basis.setAdmissionDigest(digest);
        basis.setBasisVersion(ReviewCompositionBasis.VERSION);
        basis.setBasisDigest(ReviewCompositionBasis.digest(original));
        basis.setCopyId(copy);
        basis.setExpiresAt(Instant.now().plus(ReviewCompositionBasis.RETENTION));
        var payload = mapper.createObjectNode();
        var staged = payload.putObject("files");
        original.forEach((path, bytes) -> staged.put(path, Base64.getEncoder().encodeToString(bytes)));
        basis.setPayload(payload);
        bases.save(basis);
    }

    public AdmissionResponseDTO admittedResponse(ReviewJob job) {
        return response(job, false);
    }

    public AdmissionResponseDTO capturedAdmissionResponse(ReviewJob job) {
        return response(job, true);
    }

    private AdmissionResponseDTO response(ReviewJob job, boolean duringCapture) {
        if (!isAdmitted(job)) throw new AdmissionConflictException();
        var admitted = proof.forAttempt(job.getWorkspaceId(), job.getReviewId(), job.getId(), citationAttempt(job));
        var pinned = assignments.load(job).stream()
                .collect(Collectors.toMap(a -> a.practice().practiceId(), Function.identity()));
        Set<UUID> eligible = publicEligibility.publicResultIds(job, admitted);
        var results = admitted.stream()
                .map(row -> {
                    var candidate = row.candidate();
                    var assignment = Objects.requireNonNull(pinned.get(candidate.practiceId()));
                    boolean summaryOnly = !assignment.practice().definition().lineNotes();
                    boolean publicEligible = eligible.contains(row.id());
                    var problems = row.problems().stream()
                            .map(problem -> {
                                var child = problem.candidate();
                                return new AdmittedProblemDTO(
                                        problem.id(),
                                        child.severity(),
                                        child.summary(),
                                        row.id(),
                                        child.position(),
                                        child.key(),
                                        child.explanation(),
                                        projected(child.citations(), !summaryOnly),
                                        child.basis(),
                                        child.nextStep(),
                                        publicEligible);
                            })
                            .toList();
                    JsonNode evidence = mapper.valueToTree(new ProjectedEvidence(
                            projected(candidate.evidence().citations(), false),
                                    candidate.evidence().search(),
                            candidate.evidence().inapplicability(),
                                    candidate.evidence().openQuestions().stream()
                                            .map(question -> new ProjectedQuestion(
                                                    question.openQuestion(),
                                                    question.wouldSettleIt(),
                                                    question.citation() == null
                                                            ? null
                                                            : projected(List.of(question.citation()), false)
                                                                    .getFirst()))
                                            .toList()));
                    var sourceExample = candidate.example();
                    JsonNode example = sourceExample == null
                            ? null
                            : mapper.valueToTree(new ProjectedExample(
                                    projected(List.of(sourceExample.citation()), false)
                                            .getFirst(),
                                    sourceExample.note()));
                    return new AdmittedResultDTO(
                            assignment.practiceSlug(),
                            Long.toString(candidate.itemId()),
                            row.id(),
                            candidate.outcome(),
                            candidate.summary(),
                            candidate.explanation(),
                            evidence,
                            example,
                            candidate.coverage(),
                            problems,
                            summaryOnly,
                            publicEligible);
                })
                .toList();
        var withheld = new ArrayList<AdmissionResponseDTO.Withheld>();
        JsonNode capture = job.getCapture();
        if (capture != null)
            for (var entry : capture.path("withheld")) {
                withheld.add(new AdmissionResponseDTO.Withheld(
                        entry.path("practiceSlug").asString(),
                        entry.path("itemId").asString(),
                        entry.path("reason").asString()));
            }
        FeedbackCompositionService.Read privateRead = null;
        if (assignments.review(job).kind() == ReviewKind.ITEM && !admitted.isEmpty()) {
            if (duringCapture) privateRead = composition.readForCapture(job, admitted);
            else {
                var identity = new AdmissionIdentity(
                        job.getId(), job.getWorkspaceId(), job.getAttempt(), Objects.requireNonNull(job.getWorkerId()));
                privateRead = files.indexLateRead(
                        identity,
                        () -> Objects.requireNonNull(transactions.execute(status -> {
                            ownedJob(identity);
                            return composition.read(job, admitted);
                        })),
                        read -> new JobEvidenceFiles.LateCopies(read.copies()));
            }
        }
        return new AdmissionResponseDTO(
                3,
                job.getReviewId(),
                job.getId(),
                citationAttempt(job),
                Objects.requireNonNull(job.getAdmissionDigest()),
                results,
                withheld,
                privateRead == null ? null : FeedbackCompositionService.page(privateRead),
                privateRead == null ? null : FeedbackCompositionService.conversation(privateRead),
                assignments.publicReviewed(job));
    }

    public PrepareFeedbackResponseDTO prepareFeedback(AdmissionIdentity identity, PrepareFeedbackRequestDTO request) {
        var context = files.indexLateRead(
                identity,
                () -> Objects.requireNonNull(transactions.execute(status -> {
                    var owned = ownedJob(identity);
                    requireIdentity(owned, request.reviewId(), request.jobId(), request.attempt());
                    if (!isAdmitted(owned) || assignments.review(owned).kind() != ReviewKind.ITEM)
                        throw new AdmissionConflictException();
                    return composition.read(
                            owned,
                            proof.forAttempt(
                                    owned.getWorkspaceId(),
                                    owned.getReviewId(),
                                    owned.getId(),
                                    citationAttempt(owned)));
                })),
                read -> new JobEvidenceFiles.LateCopies(read.copies()));
        return Objects.requireNonNull(transactions.execute(status -> {
            ReviewJob job = ownedJob(identity);
            requireIdentity(job, request.reviewId(), request.jobId(), request.attempt());
            if (!isAdmitted(job) || assignments.review(job).kind() != ReviewKind.ITEM)
                throw new AdmissionConflictException();
            var rows = proof.forAttempt(job.getWorkspaceId(), job.getReviewId(), job.getId(), citationAttempt(job));
            Set<UUID> resultIds = rows.stream().map(row -> row.id()).collect(Collectors.toSet());
            var allowed = new HashSet<>(resultIds);
            context.support().stream().map(row -> row.resultId()).forEach(allowed::add);
            if (!allowed.containsAll(request.basisResultIds())) throw new AdmissionConflictException();
            var anchors = new LinkedHashMap<UUID, FeedbackAnchor>();
            for (var row : rows)
                for (var problem : row.problems()) {
                    var citations = projected(problem.candidate().citations(), true);
                    citations.stream()
                            .filter(ProjectedCitationDTO::anchorable)
                            .findFirst()
                            .ifPresent(citation -> anchors.put(
                                    problem.id(),
                                    new FeedbackAnchor(
                                            Objects.requireNonNull(citation.path()),
                                            citation.startLine(),
                                            citation.endLine(),
                                            citation.side())));
                }
            var response = composition.prepare(
                    job,
                    request,
                    rows,
                    context,
                    anchors,
                    request.publicComposition() && assignments.publicReviewed(job));
            jobs.saveAndFlush(job);
            return response;
        }));
    }

    public PublicFeedbackHistoryDTO publicFeedbackHistory(AdmissionIdentity identity) {
        var retained = files.indexLateRead(
                identity,
                () -> Objects.requireNonNull(transactions.execute(status -> {
                    var job = ownedJob(identity);
                    if (!isAdmitted(job) || assignments.review(job).kind() != ReviewKind.ITEM)
                        throw new PublicHistoryRefusedException("History requires an admitted item review");
                    return history.publicSameItemFeedback(job, assignments.load(job));
                })),
                read -> new JobEvidenceFiles.LateCopies(read.copies()));
        return retained.response();
    }

    public ReviewJob ownedJob(AdmissionIdentity identity) {
        var retained = jobs.findByIdAndWorkspaceId(identity.jobId(), identity.workspaceId())
                .orElseThrow(StaleAttemptException::new);
        writeGuard.lock(identity.workspaceId(), retained.getReviewId());
        var job = jobs.findByIdAndWorkspaceIdForUpdate(identity.jobId(), identity.workspaceId())
                .orElseThrow(StaleAttemptException::new);
        if (job.getStatus() != ReviewJobStatus.RUNNING
                || job.getAttempt() != identity.attempt()
                || !identity.workerId().equals(job.getWorkerId())) throw new StaleAttemptException();
        return job;
    }

    private static void requireIdentity(ReviewJob job, UUID reviewId, UUID jobId, int attempt) {
        if (!job.getReviewId().equals(reviewId) || !job.getId().equals(jobId) || job.getAttempt() != attempt)
            throw new StaleAttemptException();
    }

    public static boolean isAdmitted(ReviewJob job) {
        return job.getAdmissionAttempt() != null && job.getAdmissionDigest() != null;
    }

    public static int citationAttempt(ReviewJob job) {
        return Objects.requireNonNull(job.getAdmissionAttempt());
    }

    public static boolean isContinuation(ReviewJob job) {
        return isAdmitted(job) && citationAttempt(job) != job.getAttempt();
    }

    public record AdmissionIdentity(UUID jobId, long workspaceId, int attempt, String workerId) {}

    private List<ProjectedCitationDTO> projected(List<EvidenceCitation> citations, boolean permitsAnchor) {
        var projected = new ArrayList<ProjectedCitationDTO>();
        for (int index = 0; index < citations.size(); index++) {
            var citation = citations.get(index);
            boolean anchorable = permitsAnchor && citation.verification() != null && citation.side() != null;
            projected.add(new ProjectedCitationDTO(
                    citation.sourceKind().value(),
                    citation.sourcePath(),
                    citation.path(),
                    citation.side(),
                    citation.commit(),
                    citation.startLine(),
                    citation.endLine(),
                    citation.quote(),
                    citation.verification(),
                    index,
                    anchorable));
        }
        return List.copyOf(projected);
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private record ProjectedEvidence(
            List<ProjectedCitationDTO> citations,
            @Nullable SearchWarrant search,
            @Nullable InapplicabilityWarrant inapplicability,
            List<ProjectedQuestion> openQuestions) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private record ProjectedQuestion(
            String openQuestion,
            String wouldSettleIt,
            @Nullable ProjectedCitationDTO citation) {}

    private record ProjectedExample(ProjectedCitationDTO citation, String note) {}

    public static class StaleAttemptException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    public static class AdmissionConflictException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    public static class PublicHistoryRefusedException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public PublicHistoryRefusedException(String message) {
            super(message);
        }

        public PublicHistoryRefusedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
