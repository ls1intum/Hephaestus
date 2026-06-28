package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationFingerprint;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.PracticeDetectionCompletedEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Persists validated practice findings and publishes a completion event.
 *
 * <p>Safe to retry — rolled-back inserts leave no trace; committed inserts are
 * deduplicated by idempotency_key via {@code ON CONFLICT DO NOTHING}.
 */
@Service
public class PracticeDetectionDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(PracticeDetectionDeliveryService.class);

    private final PracticeRepository practiceRepository;
    private final PracticeRevisionRepository practiceRevisionRepository;
    private final ObservationRepository observationRepository;
    private final PullRequestRepository pullRequestRepository;
    private final IssueRepository issueRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public PracticeDetectionDeliveryService(
        PracticeRepository practiceRepository,
        PracticeRevisionRepository practiceRevisionRepository,
        ObservationRepository observationRepository,
        PullRequestRepository pullRequestRepository,
        IssueRepository issueRepository,
        ApplicationEventPublisher eventPublisher,
        ObjectMapper objectMapper
    ) {
        this.practiceRepository = practiceRepository;
        this.practiceRevisionRepository = practiceRevisionRepository;
        this.observationRepository = observationRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.issueRepository = issueRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /** Resolved delivery target: who the finding is about + the typed (work artifact, id) reference. */
    private record Target(WorkArtifact type, Long id, Long aboutUserId) {}

    /**
     * Persist validated findings and publish completion event.
     *
     * @param job the completed agent job (must have metadata with pull_request_id)
     * @param validFindings parsed and validated findings from the result parser
     * @return delivery result with insert/discard counts
     * @throws JobDeliveryException if the target PR or author cannot be resolved
     */
    @Transactional
    public DeliveryResult deliver(AgentJob job, List<ValidatedFinding> validFindings) {
        // Extract IDs from job metadata
        Long workspaceId = job.getWorkspace().getId(); // Safe: Hibernate proxy returns FK without init
        JsonNode metadata = job.getMetadata();
        if (metadata == null) {
            throw new JobDeliveryException("Missing job metadata: jobId=" + job.getId());
        }

        // Load practice catalog for this workspace
        Map<String, Practice> practicesBySlug = practiceRepository
            .findByWorkspaceIdAndActiveTrue(workspaceId)
            .stream()
            .collect(Collectors.toMap(Practice::getSlug, p -> p, (a, b) -> a));

        if (practicesBySlug.isEmpty()) {
            log.error(
                "Workspace has no active practices — all findings discarded: workspaceId={}, jobId={}",
                workspaceId,
                job.getId()
            );
            // Empty-catalog discards fold into the discardedUnknownSlug count: with no active practices
            // every finding's slug is unknown to this workspace, so the per-finding loop would attribute
            // them identically — this early-return is just the bulk form of the same reason.
            return new DeliveryResult(0, validFindings.size(), 0, false);
        }

        // Resolve the typed target + the developer the finding is about, routing on the artifact.
        Target target = resolveTarget(job, metadata);
        Long aboutUserId = target.aboutUserId();
        WorkArtifact artifactType = target.type();
        Long artifactId = target.id();

        // Persist findings
        int inserted = 0;
        int discardedUnknownSlug = 0;
        int discardedDuplicate = 0;
        boolean hasNegative = false;
        Instant observedAt = Instant.now();

        // The exact correlation key persisted per finding, keyed by finding IDENTITY (not value-equality — two
        // findings can be value-equal yet must each carry their own key). Returned so the handler stamps the
        // SAME key onto the deliverable findings instead of recomputing it downstream, which could drift from
        // what was persisted. Only known-slug findings are entered here; unknown-slug ones are skipped below
        // (no key computed, never delivered), so the map aligns exactly with what the handler composes.
        Map<ValidatedFinding, String> findingFingerprints = new IdentityHashMap<>();

        // Current criteria-revision per practice, memoized — every finding pins to the criteria as it was
        // (SCD-2 reproducibility). Null if a practice has no revision yet (pre-versioning legacy practices).
        Map<Long, Long> revisionByPractice = new HashMap<>();

        for (int i = 0; i < validFindings.size(); i++) {
            ValidatedFinding finding = validFindings.get(i);

            // Check slug against workspace practices
            Practice practice = practicesBySlug.get(finding.practiceSlug());
            if (practice == null) {
                discardedUnknownSlug++;
                log.info(
                    "Discarded finding for unknown practice slug: slug={}, jobId={}",
                    finding.practiceSlug(),
                    job.getId()
                );
                continue;
            }

            // Build idempotency key — includes index to allow multiple findings per practice
            String idempotencyKey =
                finding.practiceSlug() + ":" + i + ":" + artifactType.name() + ":" + artifactId + ":" + job.getId();

            // Serialize evidence
            String evidenceJson = null;
            if (finding.evidence() != null) {
                try {
                    evidenceJson = objectMapper.writeValueAsString(finding.evidence());
                } catch (JacksonException e) {
                    log.debug("Failed to serialize evidence, storing null: jobId={}", job.getId());
                }
            }

            // aboutUserId is whose conduct the finding is filed against — always explicit (never null): the
            // developer for author-side practices (the whole catalogue today), the reviewer for
            // reviewer-audience practices once they ship (ADR 0021 C2).

            // Cross-run identity (ADR 0021 C2): a content-derived key that is STABLE across re-detections —
            // so a later Feedback can supersede instead of re-post and the RQ "do practices change over time"
            // becomes answerable. Derived from what the finding is ABOUT, never from the job or line number.
            String findingFingerprint = ObservationFingerprint.compute(
                finding.practiceSlug(),
                artifactType.name(),
                artifactId,
                aboutUserId,
                firstLocationPath(finding.evidence())
            );
            findingFingerprints.put(finding, findingFingerprint);

            // Insert (idempotent)
            Long practiceRevisionId = revisionByPractice.computeIfAbsent(practice.getId(), pid ->
                practiceRevisionRepository
                    .findFirstByPracticeIdOrderByRevisionNumberDesc(pid)
                    .map(rev -> rev.getId())
                    .orElse(null)
            );

            int rows = observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                idempotencyKey,
                job.getId(),
                practice.getId(),
                practiceRevisionId,
                artifactType.name(),
                artifactId,
                aboutUserId,
                finding.title(),
                finding.presence().name(),
                finding.assessment() == null ? null : finding.assessment().name(),
                finding.severity() == null ? null : finding.severity().name(),
                finding.confidence(),
                evidenceJson,
                finding.reasoning(),
                findingFingerprint,
                observedAt
            );

            if (rows == 1) {
                inserted++;
            } else {
                discardedDuplicate++;
            }
            // Track problem findings based on assessment, not insert result.
            // Critical for retry delivery: on retry, insertIfAbsent returns 0 for existing
            // findings, but we still need correct hasNegative for the delivery gate.
            if (finding.assessment() == Assessment.BAD) {
                hasNegative = true;
            }
        }

        int totalDiscarded = discardedUnknownSlug + discardedDuplicate;
        log.info(
            "Practice detection delivery: inserted={}, unknownSlug={}, duplicate={}, jobId={}",
            inserted,
            discardedUnknownSlug,
            discardedDuplicate,
            job.getId()
        );

        // Publish completion event
        eventPublisher.publishEvent(
            new PracticeDetectionCompletedEvent(
                job.getId(),
                workspaceId,
                artifactType,
                artifactId,
                aboutUserId, // the event's developerId field == aboutUserId (author-side subject today)
                inserted,
                totalDiscarded,
                hasNegative
            )
        );

        return new DeliveryResult(inserted, discardedUnknownSlug, discardedDuplicate, hasNegative, findingFingerprints);
    }

    /**
     * Route the delivery target on the job's artifact. Issue jobs carry {@code artifact_type=ISSUE} +
     * {@code issue_id}; PR jobs carry {@code pull_request_id} (no {@code artifact_type} → defaults to PR,
     * keeping existing PR jobs and replays working).
     */
    private Target resolveTarget(AgentJob job, JsonNode metadata) {
        String artifactType = metadata.has("artifact_type") ? metadata.get("artifact_type").asString() : "PULL_REQUEST";
        if (WorkArtifact.ISSUE.name().equals(artifactType)) {
            JsonNode issueIdNode = metadata.get("issue_id");
            if (issueIdNode == null || issueIdNode.isNull() || !issueIdNode.isNumber()) {
                throw new JobDeliveryException("Missing issue_id in job metadata: jobId=" + job.getId());
            }
            Long issueId = issueIdNode.asLong();
            // TYPE(i)=Issue finder: never resolve a PullRequest under an ISSUE artifact_type (shared table/id space).
            // findByIdWithAuthor fetches the author in the same query so getAuthor() below is not a lazy round-trip.
            Issue issue = issueRepository
                .findByIdWithAuthor(issueId)
                .orElseThrow(() ->
                    new JobDeliveryException("Issue not found: issueId=" + issueId + ", jobId=" + job.getId())
                );
            if (issue.getAuthor() == null) {
                throw new JobDeliveryException("Issue has no author: issueId=" + issueId + ", jobId=" + job.getId());
            }
            return new Target(WorkArtifact.ISSUE, issueId, issue.getAuthor().getId());
        }
        JsonNode pullRequestIdNode = metadata.get("pull_request_id");
        if (pullRequestIdNode == null || pullRequestIdNode.isNull() || !pullRequestIdNode.isNumber()) {
            throw new JobDeliveryException("Missing pull_request_id in job metadata: jobId=" + job.getId());
        }
        Long pullRequestId = pullRequestIdNode.asLong();
        PullRequest pullRequest = pullRequestRepository
            .findByIdWithAuthor(pullRequestId)
            .orElseThrow(() ->
                new JobDeliveryException(
                    "Pull request not found: pullRequestId=" + pullRequestId + ", jobId=" + job.getId()
                )
            );
        if (pullRequest.getAuthor() == null) {
            throw new JobDeliveryException(
                "Pull request has no author: pullRequestId=" + pullRequestId + ", jobId=" + job.getId()
            );
        }
        return new Target(WorkArtifact.PULL_REQUEST, pullRequestId, pullRequest.getAuthor().getId());
    }

    /**
     * The file path of a finding's first evidence location, or {@code null} when it has none (a metadata
     * practice like PR-description quality). Feeds {@link ObservationFingerprint} — the PATH only, never a line
     * number, so a finding that survives a few lines moving keeps one cross-run identity. Package-private so
     * {@code ReactionSuppressionFilter} (B2) recomputes the same locus the SAME way.
     */
    static String firstLocationPath(JsonNode evidence) {
        if (evidence == null || evidence.isNull()) {
            return null;
        }
        JsonNode locations = evidence.get("locations");
        if (locations == null || !locations.isArray() || locations.isEmpty()) {
            return null;
        }
        JsonNode first = locations.get(0);
        if (first == null || !first.isObject()) {
            return null;
        }
        JsonNode path = first.get("path");
        return path != null && path.isString() ? path.asString() : null;
    }

    /**
     * @param findingFingerprints the stable cross-run key persisted for each delivered finding, keyed by finding
     *     identity, so the caller can stamp the SAME key onto its deliverable findings without recomputing it
     *     (no drift from what was persisted). Empty when no findings were persisted.
     */
    public record DeliveryResult(
        int inserted,
        int discardedUnknownSlug,
        int discardedDuplicate,
        boolean hasNegative,
        Map<ValidatedFinding, String> findingFingerprints
    ) {
        /** Compatibility shape for call sites/tests that do not consume per-finding correlation keys. */
        public DeliveryResult(int inserted, int discardedUnknownSlug, int discardedDuplicate, boolean hasNegative) {
            this(inserted, discardedUnknownSlug, discardedDuplicate, hasNegative, Map.of());
        }
    }
}
