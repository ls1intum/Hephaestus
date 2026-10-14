package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.JobFolderIndex;
import de.tum.cit.aet.hephaestus.agent.handler.candidate.Candidate;
import de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateParser;
import de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateRefusedException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedResults;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ResultsRefusedException;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobAssignments;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.AdmissionRequestDTO;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.AdmissionResponseDTO;
import de.tum.cit.aet.hephaestus.evidence.ClasspathSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.spi.CandidateRefusal;
import de.tum.cit.aet.hephaestus.practices.spi.ClaimBasis;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceCitation;
import de.tum.cit.aet.hephaestus.practices.spi.InapplicabilityWarrant;
import de.tum.cit.aet.hephaestus.practices.spi.OpenQuestion;
import de.tum.cit.aet.hephaestus.practices.spi.ProblemCandidate;
import de.tum.cit.aet.hephaestus.practices.spi.ResultCandidate;
import de.tum.cit.aet.hephaestus.practices.spi.ResultCoverage;
import de.tum.cit.aet.hephaestus.practices.spi.ResultEvidence;
import de.tum.cit.aet.hephaestus.practices.spi.ResultExample;
import de.tum.cit.aet.hephaestus.practices.spi.ResultStore;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentStore;
import de.tum.cit.aet.hephaestus.practices.spi.SearchWarrant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@ConditionalOnCaptureRole
public class ReviewOutputService {
    private final ReviewJobAssignments assignments;
    private final CandidateEvidenceVerifier verifier;
    private final ResultStore results;
    private final ReviewAssignmentStore assignmentStore;
    private final JobEvidenceFiles files;
    private final ClasspathSourceCatalogRegistry catalogs;
    private final ObjectMapper mapper;

    public ReviewOutputService(
            ReviewJobAssignments assignments,
            CandidateEvidenceVerifier verifier,
            ResultStore results,
            ObjectMapper mapper,
            JobEvidenceFiles files,
            ClasspathSourceCatalogRegistry catalogs,
            ReviewAssignmentStore assignmentStore) {
        this.assignments = assignments;
        this.verifier = verifier;
        this.results = results;
        this.mapper = mapper;
        this.files = files;
        this.catalogs = catalogs;
        this.assignmentStore = assignmentStore;
    }

    public PreparedResults prepare(ReviewJob job, AdmissionRequestDTO submission) {
        if (!job.getReviewId().equals(submission.reviewId())
                || !job.getId().equals(submission.jobId())
                || job.getAttempt() != submission.attempt()) throw new ResultAdmissionService.StaleAttemptException();
        Map<String, ReviewAssignmentSnapshot> pinned = assignments.admissible(job).stream()
                .collect(Collectors.toUnmodifiableMap(ReviewAssignmentSnapshot::practiceSlug, Function.identity()));
        var review = assignments.review(job);
        JsonNode originalCapture = Objects.requireNonNull(job.getCapture());
        var manifest = mapper.treeToValue(originalCapture.path("manifest"), JobFolderIndex.class);
        var receipts = submission.results().stream()
                .flatMap(entry -> entry.readReceipts().stream())
                .toList();
        var resolvedManifest = files.registerReadReceipts(job, manifest, receipts, catalogs);
        var captured = CapturedEvidence.of(resolvedManifest);
        var admitted = new ArrayList<ResultCandidate>();
        var withheld = new LinkedHashMap<String, AdmissionResponseDTO.Withheld>();
        var seen = new HashSet<String>();
        var refusals = new ArrayList<WithheldAssignment>();
        for (var entry : submission.results()) {
            if (!seen.add(entry.practiceSlug()))
                throw new ResultsRefusedException("ambiguous_practice_results", "A practice repeats in the submission");
            var assignment = pinned.get(entry.practiceSlug());
            if (assignment == null)
                throw new ResultsRefusedException("practice_not_admitted", "The practice is not assigned to this job");
            if (!Long.toString(assignment.item().itemId()).equals(entry.itemId())
                    || !assignment.version().value().equals(entry.itemVersion())) {
                throw new ResultsRefusedException(
                        "wrong_attribution", "The submitted item or version differs from the frozen assignment");
            }
            Candidate candidate;
            try {
                candidate = CandidateParser.parse(entry.candidate());
            } catch (CandidateRefusedException refused) {
                refusals.add(new WithheldAssignment(
                        assignment, refused.refusal(), Objects.requireNonNull(refused.getMessage())));
                continue;
            }
            if (candidate.coverage().path("source").asString().equals("host")
                    && !candidate.coverage().path("receipts").equals(mapper.valueToTree(entry.readReceipts()))) {
                refusals.add(new WithheldAssignment(
                        assignment,
                        CandidateRefusal.COVERAGE_INCONSISTENT,
                        "Host coverage differs from the submitted read receipts"));
                continue;
            }
            try {
                verifier.verifyCandidate(job, assignment, review.kind(), candidate, captured);
                admitted.add(result(assignment, candidate));
            } catch (CandidateEvidenceVerifier.Unverified refusal) {
                refusals.add(new WithheldAssignment(
                        assignment, refusal.refusal(), Objects.requireNonNull(refusal.getMessage())));
            }
        }
        if (submission.results().isEmpty() || !seen.equals(pinned.keySet())) {
            throw new ResultsRefusedException(
                    "no_admissible_results", "Every assigned practice must submit one result");
        }
        int attempt = job.getAttempt();
        String worker = job.getWorkerId();
        JsonNode capture = Objects.requireNonNull(job.getCapture()).deepCopy();
        return owned -> {
            if (!job.getId().equals(owned.getId())
                    || attempt != owned.getAttempt()
                    || !Objects.equals(worker, owned.getWorkerId())
                    || !capture.equals(owned.getCapture())) {
                throw new ResultAdmissionService.StaleAttemptException();
            }
            for (var refusal : refusals) {
                assignmentStore.withhold(
                        owned.getWorkspaceId(), refusal.assignment().id(), refusal.reason(), refusal.message());
                var entry = new AdmissionResponseDTO.Withheld(
                        refusal.assignment().practiceSlug(),
                        Long.toString(refusal.assignment().item().itemId()),
                        refusal.reason().wireValue());
                withheld.put(entry.practiceSlug(), entry);
            }
            var receipt = results.admit(
                    owned.getWorkspaceId(), owned.getReviewId(), owned.getId(), attempt, List.copyOf(admitted));
            for (var item : receipt.withheldItems()) {
                var assignment = pinned.values().stream()
                        .filter(value -> value.practice().practiceId() == item.practiceId()
                                && value.item().itemId() == item.itemId())
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Admission withheld an unassigned item"));
                withheld.putIfAbsent(
                        assignment.practiceSlug(),
                        new AdmissionResponseDTO.Withheld(
                                assignment.practiceSlug(), Long.toString(item.itemId()), item.reason()));
            }
            ObjectNode retained = (ObjectNode) capture.deepCopy();
            retained.set("manifest", mapper.valueToTree(resolvedManifest));
            retained.set("readReceipts", mapper.valueToTree(receipts));
            retained.set("withheld", mapper.valueToTree(withheld.values()));
            retained.set("admission", mapper.valueToTree(receipt));
            owned.setCapture(retained);
        };
    }

    private record WithheldAssignment(ReviewAssignmentSnapshot assignment, CandidateRefusal reason, String message) {}

    private ResultCandidate result(ReviewAssignmentSnapshot assignment, Candidate candidate) {
        var problems = new ArrayList<ProblemCandidate>();
        for (int i = 0; i < candidate.problems().size(); i++) {
            var problem = candidate.problems().get(i);
            problems.add(new ProblemCandidate(
                    i,
                    problem.key(),
                    problem.severity(),
                    problem.summary(),
                    problem.explanation(),
                    problem.citations().stream().map(this::citation).toList(),
                    mapper.treeToValue(problem.basis(), ClaimBasis.class),
                    problem.nextStep()));
        }
        var evidence = new ResultEvidence(
                candidate.citations().stream().map(this::citation).toList(),
                candidate.search() == null ? null : mapper.treeToValue(candidate.search(), SearchWarrant.class),
                candidate.inapplicability() == null
                        ? null
                        : mapper.treeToValue(candidate.inapplicability(), InapplicabilityWarrant.class),
                candidate.openQuestions().stream()
                        .map(question -> mapper.treeToValue(question, OpenQuestion.class))
                        .toList());
        return new ResultCandidate(
                assignment.practice().practiceId(),
                assignment.practice().practiceVersionId(),
                assignment.item().itemId(),
                assignment.version().value(),
                assignment.developerId(),
                candidate.outcome(),
                candidate.summary(),
                candidate.explanation(),
                evidence,
                candidate.example() == null ? null : mapper.treeToValue(candidate.example(), ResultExample.class),
                mapper.treeToValue(candidate.coverage(), ResultCoverage.class),
                problems);
    }

    private EvidenceCitation citation(ObjectNode citation) {
        return mapper.treeToValue(citation, EvidenceCitation.class);
    }
}
