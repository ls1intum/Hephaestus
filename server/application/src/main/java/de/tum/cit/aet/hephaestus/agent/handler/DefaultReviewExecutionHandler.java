package de.tum.cit.aet.hephaestus.agent.handler;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.agent.context.AppliesWhenEvaluator;
import de.tum.cit.aet.hephaestus.agent.context.InsufficientEvidenceException;
import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.JobFolderIndex;
import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.context.providers.ReviewHistoryContentSource;
import de.tum.cit.aet.hephaestus.agent.handler.composition.FeedbackCompositionInputs;
import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedJobInputs;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobAssignments;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.AdmissionRequestDTO;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.task.Task;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelope;
import de.tum.cit.aet.hephaestus.evidence.AppliesWhenCheck;
import de.tum.cit.aet.hephaestus.evidence.AutomatedReviewReadinessDecision;
import de.tum.cit.aet.hephaestus.evidence.AutomatedReviewReadinessReason;
import de.tum.cit.aet.hephaestus.evidence.AutomatedReviewReadinessReport;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceReadinessCheck;
import de.tum.cit.aet.hephaestus.evidence.SourceReadinessReason;
import de.tum.cit.aet.hephaestus.evidence.capture.CaptureRequest;
import de.tum.cit.aet.hephaestus.integration.core.framework.ItemDescriptorRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSource;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScanItemVersions;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.ReadNeed;
import de.tum.cit.aet.hephaestus.practices.spi.ResultEvidence;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentStore;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewCaptureDecision;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewPracticeStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedByKind;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class DefaultReviewExecutionHandler {
    private final ReviewJobAssignments assignments;
    private final List<ItemSource> sources;
    private final WorkspaceContextBuilder context;
    private final JobEvidenceFiles files;
    private final PracticeCatalogInjector catalog;
    private final PracticeScriptInputs inputs;
    private final ReviewHistoryContentSource history;
    private final ReviewAssignmentStore capture;
    private final ItemDescriptorRegistry items;
    private final List<ScanItemVersions> scanVersions;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final AppliesWhenEvaluator applicability;
    private final ResultAdmissionService admission;

    public PreparedJobInputs prepareInputs(ReviewJob job) {
        var review = assignments.review(job);
        var pinned = assignments.load(job);
        var item = pinned.getFirst().item();
        var matching = sources.stream().filter(source -> source.supports(item)).toList();
        if (matching.size() != 1) throw new IllegalStateException("The item needs exactly one capture source");
        var source = matching.getFirst();
        var contracts = pinned.stream()
                .map(a -> (review.kind() == ReviewKind.SCAN
                                ? Objects.requireNonNull(
                                                a.practice().definition().scan())
                                        .reads()
                                : a.practice().definition().reads())
                        .contract())
                .distinct()
                .toList();
        if (!contracts.equals(List.of(SandboxLayout.SOURCE_CONTRACT_VERSION)))
            throw new IllegalArgumentException("The assigned source contract is not executable");
        var required = pinned.stream()
                .flatMap(a -> (review.kind() == ReviewKind.SCAN
                                ? Objects.requireNonNull(
                                                a.practice().definition().scan())
                                        .reads()
                                : a.practice().definition().reads())
                        .sources().stream())
                .map(read -> read.source())
                .collect(Collectors.toSet());
        for (var assignment : pinned) {
            var conditions = assignment.practice().definition().appliesWhen();
            if (review.kind() == ReviewKind.ITEM && conditions != null)
                required.addAll(applicability.sources(item.kind(), conditions));
        }
        var grantedContext = new LinkedHashMap<ReviewAssignmentId, List<String>>();
        if (review.kind() == ReviewKind.SCAN) {
            var composers = scanVersions.stream()
                    .filter(v -> v.kinds().contains(item.kind()))
                    .toList();
            if (composers.size() != 1)
                throw new IllegalStateException("The scan item needs exactly one version composer");
            var composer = composers.getFirst();
            for (var assignment : pinned)
                grantedContext.put(
                        assignment.id(),
                        composer.contextPaths(
                                assignment.item(),
                                assignment.version(),
                                Objects.requireNonNull(assignment
                                                .practice()
                                                .definition()
                                                .scan())
                                        .context()));
        }
        var snapshot = source.pin(item, pinned.getFirst().version());
        files.beginPersonCapture(job);
        try {
            var evidence = context.prepare(
                    snapshot,
                    new CaptureRequest(
                            review.id(),
                            job.getId(),
                            job.getAttempt(),
                            required,
                            contracts.getFirst(),
                            SandboxLayout.PRACTICE_CONTRACT_VERSION));
            try {
                var manifest = Objects.requireNonNull(evidence.manifest());
                var now = clock.instant();
                var decisions = pinned.stream()
                        .map(a -> readiness(a, manifest, now, review.kind()))
                        .toList();
                var checks = new LinkedHashMap<String, AppliesWhenCheck>();
                for (var assignment : pinned) {
                    if (decisions.stream()
                            .noneMatch(d -> d.practiceSlug().equals(assignment.practiceSlug()) && d.ready())) continue;
                    var check = applicability.evaluate(
                            assignment.practice().definition().appliesWhen(), manifest, evidence.files());
                    if (check != null) checks.put(assignment.practiceSlug(), check);
                }
                var report = new AutomatedReviewReadinessReport(
                        manifest.contractVersion(),
                        manifest.catalogDigest(),
                        item.kind().value(),
                        manifest.capturedAt(),
                        now,
                        decisions,
                        checks);
                capture.capture(
                        job.getWorkspaceId(),
                        review.id(),
                        job.getId(),
                        job.getAttempt(),
                        decisions.stream()
                                .map(decision -> {
                                    var assignment = pinned.stream()
                                            .filter(a -> a.practiceSlug().equals(decision.practiceSlug()))
                                            .findFirst()
                                            .orElseThrow();
                                    return new ReviewCaptureDecision(
                                            assignment.practice().practiceId(),
                                            decision.ready()
                                                    ? ReviewPracticeStatus.READY
                                                    : ReviewPracticeStatus.WITHHELD,
                                            decision.ready() ? null : "readiness_refused",
                                            captureFacts(decision, checks.get(decision.practiceSlug())));
                                })
                                .toList());
                var staged = new LinkedHashMap<>(evidence.files());
                var ready = pinned.stream()
                        .filter(a -> decisions.stream()
                                .anyMatch(d -> d.practiceSlug().equals(a.practiceSlug()) && report.runs(d)))
                        .toList();
                catalog.inject(staged, ready, review.kind(), grantedContext);
                if (!ready.isEmpty()) {
                    var record = source.record(snapshot, evidence.files());
                    for (var assignment : ready) {
                        JsonNode previous = mapper.nullNode();
                        if (review.kind() == ReviewKind.ITEM) {
                            var retained = history.previousResults(assignment);
                            if (!retained.isEmpty()) {
                                var prior = retained.getFirst();
                                var coordinates = mapper.createObjectNode()
                                        .put("resultId", prior.resultId().toString())
                                        .put("reviewedAt", prior.recordedAt().toString())
                                        .put("origin", prior.origin().name())
                                        .put("itemVersion", prior.itemVersion())
                                        .put("outcome", prior.outcome().name());
                                var priorEvidence = prior.evidence();
                                if (priorEvidence != null) {
                                    coordinates.set("citations", previousCitations(priorEvidence));
                                    previous = coordinates;
                                }
                            }
                        }
                        staged.putAll(inputs.stage(
                                review,
                                assignment,
                                record,
                                Objects.requireNonNullElse(job.getStartedAt(), now),
                                previous));
                    }
                }
                staged.put(
                        SandboxLayout.TASK_ENVELOPE_FILENAME,
                        mapper.writeValueAsBytes(TaskEnvelope.of(
                                review.id(),
                                job.getId(),
                                job.getAttempt(),
                                job.getWorkspaceId(),
                                new Task("Review each assigned practice against the captured item. Use the"
                                        + " governed tools and return one result for each assigned"
                                        + " practice."))));
                FeedbackCompositionInputs.stage(
                        staged,
                        review.kind(),
                        review.origin(),
                        Set.of(
                                FeedbackDestination.ON_WORK,
                                FeedbackDestination.PRACTICE_PAGE,
                                FeedbackDestination.CONVERSATION),
                        items.descriptorFor(item.kind())
                                .map(ItemDescriptor::copyCapabilities)
                                .orElseThrow(
                                        () -> new IllegalStateException("The captured item kind has no descriptor")),
                        mapper);
                var prepared = files.prepare(job, evidence.withFiles(staged), report);
                if (decisions.stream().noneMatch(AutomatedReviewReadinessDecision::ready))
                    throw new InsufficientEvidenceException(
                            "No assigned practice has its required captured evidence", prepared);
                return prepared;
            } catch (RuntimeException failure) {
                evidence.close();
                throw failure;
            }
        } catch (RuntimeException failure) {
            files.abortPersonCapture(job);
            throw failure;
        }
    }

    private JsonNode captureFacts(AutomatedReviewReadinessDecision decision, @Nullable AppliesWhenCheck check) {
        ObjectNode facts = mapper.valueToTree(decision);
        if (check != null) facts.set("applicability", mapper.valueToTree(check));
        return facts;
    }

    public void completeWithoutExecution(ReviewJob job) {
        admission.admit(
                new ResultAdmissionService.AdmissionIdentity(
                        job.getId(), job.getWorkspaceId(), job.getAttempt(), Objects.requireNonNull(job.getWorkerId())),
                new AdmissionRequestDTO(3, job.getReviewId(), job.getId(), job.getAttempt(), List.of(), List.of()));
    }

    private JsonNode previousCitations(JsonNode evidence) {
        var stored = mapper.treeToValue(evidence, ResultEvidence.class);
        return mapper.valueToTree(stored.citations().stream()
                .map(c -> new PreviousCitation(
                        c.sourceKind().value(),
                        c.sourcePath(),
                        c.path(),
                        c.startLine(),
                        c.endLine(),
                        c.side() == null ? null : c.side().name(),
                        c.commit()))
                .toList());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record PreviousCitation(
            String sourceKind,
            String sourcePath,
            String path,
            int startLine,
            int endLine,
            @Nullable String side,
            @Nullable String commit) {}

    private AutomatedReviewReadinessDecision readiness(
            ReviewAssignmentSnapshot assignment, JobFolderIndex manifest, Instant now, ReviewKind scope) {
        var definition = assignment.practice().definition();
        var reasons = new ArrayList<AutomatedReviewReadinessReason>();
        if (definition.reviewedBy().by() != ReviewedByKind.HEPHAESTUS)
            reasons.add(AutomatedReviewReadinessReason.NO_AUTOMATED_REVIEW);
        var reads = scope == ReviewKind.SCAN
                ? Objects.requireNonNull(definition.scan()).reads()
                : definition.reads();
        var checks = reads.sources().stream()
                .map(read -> {
                    var state = manifest.sources().stream()
                            .filter(s -> s.kind().equals(read.source()))
                            .map(s -> s.state())
                            .findFirst()
                            .orElse(null);
                    var missing = new ArrayList<SourceReadinessReason>();
                    if (read.need() != ReadNeed.OPTIONAL) {
                        if (!(state instanceof SourceCaptureState.Available available)) {
                            missing.add(SourceReadinessReason.SOURCE_NOT_AVAILABLE);
                            if (state instanceof SourceCaptureState.Unavailable unavailable) {
                                switch (unavailable.reasonCode()) {
                                    case REPOSITORY_FETCH_FAILED ->
                                        missing.add(SourceReadinessReason.REPOSITORY_FETCH_FAILED);
                                    case REVIEW_VERSION_UNAVAILABLE ->
                                        missing.add(SourceReadinessReason.REVIEW_VERSION_UNAVAILABLE);
                                    default -> {}
                                }
                            }
                        } else if (read.need() == ReadNeed.COMPLETE
                                && available.completeness() != SourceCompleteness.COMPLETE)
                            missing.add(SourceReadinessReason.SOURCE_INCOMPLETE);
                    }
                    return new SourceReadinessCheck(
                            read.source(),
                            manifest.contractVersion(),
                            now,
                            manifest.capturedAt(),
                            missing.isEmpty(),
                            missing);
                })
                .toList();
        if (checks.stream().anyMatch(check -> !check.meetsRequirements()))
            reasons.add(AutomatedReviewReadinessReason.DECLARED_EVIDENCE_INSUFFICIENT);
        return new AutomatedReviewReadinessDecision(assignment.practiceSlug(), now, reasons.isEmpty(), reasons, checks);
    }
}
