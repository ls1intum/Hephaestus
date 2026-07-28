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
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationFingerprint;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.PracticeDetectionCompletedEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
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
        Long workspaceId = job.getWorkspace().getId(); // Hibernate proxy returns the FK without initialization
        JsonNode metadata = job.getMetadata();
        if (metadata == null) {
            throw new JobDeliveryException("Missing job metadata: jobId=" + job.getId());
        }

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
            // With no active practices every slug is unknown, so the discards fold into discardedUnknownSlug.
            return new DeliveryResult(0, validFindings.size(), 0, false);
        }

        Target target = resolveTarget(job, metadata);
        Long aboutUserId = target.aboutUserId();
        WorkArtifact artifactType = target.type();
        Long artifactId = target.id();

        int inserted = 0;
        int discardedUnknownSlug = 0;
        int discardedDuplicate = 0;
        boolean hasNegative = false;
        Instant observedAt = Instant.now();

        // Keyed by finding IDENTITY, not value-equality — two findings can be value-equal yet must each carry
        // their own keys. Only known-slug findings are entered; unknown-slug ones never get keys.
        Map<ValidatedFinding, ObservationKeys> observationKeys = new IdentityHashMap<>();

        // Criteria-revision per practice, memoized. Null if a practice has no revision yet (pre-versioning).
        Instant criteriaAsOf = job.getStartedAt();
        Map<Long, Long> revisionByPractice = new HashMap<>();

        for (int i = 0; i < validFindings.size(); i++) {
            ValidatedFinding finding = validFindings.get(i);

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

            // The index disambiguates multiple findings for the same practice on one artifact.
            String occurrenceKey =
                finding.practiceSlug() + ":" + i + ":" + artifactType.name() + ":" + artifactId + ":" + job.getId();

            String evidenceJson = null;
            if (finding.evidence() != null) {
                try {
                    evidenceJson = objectMapper.writeValueAsString(finding.evidence());
                } catch (JacksonException e) {
                    log.debug("Failed to serialize evidence, storing null: jobId={}", job.getId());
                }
            }

            // Cross-run identity (ADR 0021 C2): a content-derived key that is STABLE across re-detections —
            // so a later Feedback can supersede instead of re-post and the RQ "do practices change over time"
            // becomes answerable. Derived from what the finding is ABOUT, never from the job or line number.
            String recurrenceKey = ObservationFingerprint.compute(
                finding.practiceSlug(),
                artifactType.name(),
                artifactId,
                aboutUserId,
                firstLocationPath(finding.evidence())
            );
            observationKeys.put(finding, new ObservationKeys(occurrenceKey, recurrenceKey));

            Long practiceRevisionId = revisionByPractice.computeIfAbsent(practice.getId(), pid ->
                resolvePinnedRevisionId(pid, criteriaAsOf)
            );

            // Self-enforce the ADR-0022 invariant that Observation.@PrePersist applies but the native
            // insertIfAbsent path bypasses: severity is an impact band for a BAD observation only, so it
            // must be null unless the assessment is BAD. Idempotent for an already-coerced finding.
            String severityName =
                finding.assessment() == Assessment.BAD && finding.severity() != null ? finding.severity().name() : null;

            int rows = observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                occurrenceKey,
                job.getId(),
                practice.getId(),
                practiceRevisionId,
                artifactType.name(),
                artifactId,
                aboutUserId,
                finding.title(),
                finding.presence().name(),
                finding.assessment() == null ? null : finding.assessment().name(),
                severityName,
                finding.confidence(),
                evidenceJson,
                finding.reasoning(),
                recurrenceKey,
                observedAt
            );

            if (rows == 1) {
                inserted++;
            } else {
                discardedDuplicate++;
            }
            // Gate on the assessment, not the insert result: a retry's insertIfAbsent returns 0 for an
            // already-persisted finding, yet hasNegative must still reflect it for the delivery gate.
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

        return new DeliveryResult(inserted, discardedUnknownSlug, discardedDuplicate, hasNegative, observationKeys);
    }

    /**
     * The criteria revision the detector was actually given: the latest that existed when the job's inputs were
     * prepared. {@code startedAt} is stamped at claim, immediately before the catalog injector reads the
     * criteria into the sandbox, so a revision an admin appends mid-run is a rubric this run never saw. Falls
     * back to the latest revision when the as-of lookup finds none (a practice created mid-run) or the job
     * carries no {@code startedAt}; null when the practice has no revision at all (pre-versioning rows).
     */
    private Long resolvePinnedRevisionId(Long practiceId, @Nullable Instant asOf) {
        if (asOf != null) {
            Optional<PracticeRevision> pinned =
                practiceRevisionRepository.findFirstByPracticeIdAndCreatedAtLessThanEqualOrderByRevisionNumberDesc(
                    practiceId,
                    asOf
                );
            if (pinned.isPresent()) {
                return pinned.get().getId();
            }
        }
        return practiceRevisionRepository
            .findFirstByPracticeIdOrderByRevisionNumberDesc(practiceId)
            .map(PracticeRevision::getId)
            .orElse(null);
    }

    /**
     * Route the delivery target on the job's artifact. Issue and conversation jobs stamp
     * {@code artifact_type}; PR jobs omit it by convention (they carry only {@code pull_request_id}), so
     * the missing discriminator defaults to PULL_REQUEST.
     */
    private Target resolveTarget(AgentJob job, JsonNode metadata) {
        String artifactType = metadata.has("artifact_type") ? metadata.get("artifact_type").asString() : "PULL_REQUEST";
        if (WorkArtifact.CONVERSATION_THREAD.name().equals(artifactType)) {
            // Repo-less: the subject user is carried EXPLICITLY in metadata (about_user_id), not resolved
            // from an SCM artifact author. artifactId is the slack_thread aggregate id.
            JsonNode threadIdNode = metadata.get("slack_thread_id");
            if (threadIdNode == null || threadIdNode.isNull() || !threadIdNode.isNumber()) {
                throw new JobDeliveryException("Missing slack_thread_id in job metadata: jobId=" + job.getId());
            }
            JsonNode aboutUserNode = metadata.get("about_user_id");
            if (aboutUserNode == null || aboutUserNode.isNull() || !aboutUserNode.isNumber()) {
                throw new JobDeliveryException("Missing about_user_id in job metadata: jobId=" + job.getId());
            }
            return new Target(WorkArtifact.CONVERSATION_THREAD, threadIdNode.asLong(), aboutUserNode.asLong());
        }
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
     * {@code ReactionSuppressionFilter} recomputes the same locus the SAME way.
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
     * @param observationKeys the keys persisted for each delivered finding, by finding identity, so the caller
     *     stamps the SAME keys onto its deliverable findings rather than recomputing them (no drift from what
     *     was persisted). Empty when no findings were persisted.
     */
    public record DeliveryResult(
        int inserted,
        int discardedUnknownSlug,
        int discardedDuplicate,
        boolean hasNegative,
        Map<ValidatedFinding, ObservationKeys> observationKeys
    ) {
        /** Compatibility shape for call sites/tests that do not consume per-finding keys. */
        public DeliveryResult(int inserted, int discardedUnknownSlug, int discardedDuplicate, boolean hasNegative) {
            this(inserted, discardedUnknownSlug, discardedDuplicate, hasNegative, Map.of());
        }
    }
}
