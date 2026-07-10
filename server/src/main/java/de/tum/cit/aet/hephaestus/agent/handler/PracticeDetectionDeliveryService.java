package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedObservation;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.ReviewerAudiencePractices;
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
    private final UserRepository userRepository;
    private final ReviewerResolver reviewerResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public PracticeDetectionDeliveryService(
        PracticeRepository practiceRepository,
        PracticeRevisionRepository practiceRevisionRepository,
        ObservationRepository observationRepository,
        PullRequestRepository pullRequestRepository,
        IssueRepository issueRepository,
        UserRepository userRepository,
        ReviewerResolver reviewerResolver,
        ApplicationEventPublisher eventPublisher,
        ObjectMapper objectMapper
    ) {
        this.practiceRepository = practiceRepository;
        this.practiceRevisionRepository = practiceRevisionRepository;
        this.observationRepository = observationRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.issueRepository = issueRepository;
        this.userRepository = userRepository;
        this.reviewerResolver = reviewerResolver;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolved delivery target: the typed (work artifact, id) reference plus the ARTIFACT AUTHOR (id + the
     * {@link User} row). The author drives the completion event and is the subject for author-audience
     * findings; reviewer-audience findings resolve their own per-row subject from the reviewer set.
     */
    private record Target(WorkArtifact type, Long id, User author) {}

    /**
     * Persist validated findings and publish completion event.
     *
     * @param job the completed agent job (must have metadata with pull_request_id)
     * @param validObservations parsed and validated findings from the result parser
     * @return delivery result with insert/discard counts
     * @throws JobDeliveryException if the target PR or author cannot be resolved
     */
    @Transactional
    public DeliveryResult deliver(AgentJob job, List<ValidatedObservation> validObservations) {
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
            // Empty-catalog discards fold into the discardedUnknownSlug count: with no active practices
            // every finding's slug is unknown to this workspace, so the per-finding loop would attribute
            // them identically — this early-return is just the bulk form of the same reason.
            return new DeliveryResult(0, validObservations.size(), 0, false);
        }

        // Resolve the typed target + the artifact author, routing on the artifact.
        Target target = resolveTarget(job, metadata);
        Long authorId = target.author().getId();
        WorkArtifact artifactType = target.type();
        Long artifactId = target.id();

        // Reviewer-audience practices (constructive-code-review) file their finding against the REVIEWER whose
        // comments were assessed, not the PR author. Resolve the real reviewer set ONCE per job — the server
        // owns reviewer identity, never the model — and only when it can matter (a reviewer-audience finding is
        // present AND the artifact is a PR). Null when not needed; empty when the PR drew no resolvable reviewer
        // (author/bots only), in which case every reviewer-audience finding is DISCARDED, never misattributed.
        Map<String, User> reviewersByLogin = null;
        boolean anyReviewerAudience = validObservations
            .stream()
            .anyMatch(f -> ReviewerAudiencePractices.isReviewerAudience(f.practiceSlug()));
        if (anyReviewerAudience && artifactType == WorkArtifact.PULL_REQUEST) {
            reviewersByLogin = reviewerResolver.reviewersByLogin(artifactId, target.author());
        }

        int inserted = 0;
        int discardedUnknownSlug = 0;
        int discardedDuplicate = 0;
        int discardedUnresolvedReviewer = 0;
        boolean hasNegative = false;
        Instant observedAt = Instant.now();

        // The exact correlation key persisted per finding, keyed by finding IDENTITY (not value-equality — two
        // findings can be value-equal yet must each carry their own key). Returned so the handler stamps the
        // SAME key onto the deliverable findings instead of recomputing it downstream, which could drift from
        // what was persisted. Only known-slug findings are entered here; unknown-slug ones are skipped below
        // (no key computed, never delivered), so the map aligns exactly with what the handler composes.
        Map<ValidatedObservation, String> findingFingerprints = new IdentityHashMap<>();

        // Current criteria-revision per practice, memoized — every finding pins to the criteria as it was
        // (SCD-2 reproducibility). Null if a practice has no revision yet (pre-versioning legacy practices).
        Map<Long, Long> revisionByPractice = new HashMap<>();

        for (int i = 0; i < validObservations.size(); i++) {
            ValidatedObservation finding = validObservations.get(i);

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

            // Per-finding subject (ADR 0021 C2): author-audience findings (the bulk of the catalogue) are filed
            // against the artifact author; reviewer-audience findings against the resolved reviewer. Correctness
            // rule #1 — NEVER fall back to the author for a reviewer-audience finding whose reviewer cannot be
            // resolved: that would misattribute a reviewer's craft to the author. DISCARD it instead.
            Long subjectUserId;
            if (!ReviewerAudiencePractices.isReviewerAudience(finding.practiceSlug())) {
                subjectUserId = authorId;
            } else if (reviewersByLogin == null || reviewersByLogin.isEmpty()) {
                discardedUnresolvedReviewer++;
                log.info(
                    "Discarded reviewer-audience finding — no resolvable reviewer on this PR: slug={}, subjectLogin={}, jobId={}",
                    finding.practiceSlug(),
                    finding.subjectLogin(),
                    job.getId()
                );
                continue;
            } else {
                String normalized = ReviewerResolver.normalizeLogin(finding.subjectLogin());
                User reviewer = normalized == null ? null : reviewersByLogin.get(normalized);
                if (reviewer != null) {
                    // (a) The model's proposed login resolves to a real reviewer.
                    subjectUserId = reviewer.getId();
                } else if (normalized == null && reviewersByLogin.size() == 1) {
                    // (b) Model named nobody and exactly one reviewer exists: deterministic fast path.
                    //     Guarded on normalized == null — when the model DID name a subject and that name
                    //     resolves to no reviewer, the observation is about someone outside the roster
                    //     (often the author or a bot); silently re-pinning it on the sole reviewer would
                    //     misattribute, so that case falls through to the discard below.
                    subjectUserId = reviewersByLogin.values().iterator().next().getId();
                } else {
                    // (c) Named-but-unresolvable subject, or multiple reviewers with no usable
                    //     subjectLogin → cannot attribute → discard.
                    discardedUnresolvedReviewer++;
                    log.info(
                        "Discarded reviewer-audience finding — subjectLogin unresolved among {} reviewers: slug={}, subjectLogin={}, jobId={}",
                        reviewersByLogin.size(),
                        finding.practiceSlug(),
                        finding.subjectLogin(),
                        job.getId()
                    );
                    continue;
                }
            }

            // The index disambiguates multiple findings for the same practice on one artifact.
            String idempotencyKey =
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
            // becomes answerable. Derived from what the finding is ABOUT (the subject), never from the job or
            // line number, so a reviewer's finding keeps its own identity across runs.
            String findingFingerprint = ObservationFingerprint.compute(
                finding.practiceSlug(),
                artifactType.name(),
                artifactId,
                subjectUserId,
                firstLocationPath(finding.evidence())
            );
            findingFingerprints.put(finding, findingFingerprint);

            Long practiceRevisionId = revisionByPractice.computeIfAbsent(practice.getId(), pid ->
                practiceRevisionRepository
                    .findFirstByPracticeIdOrderByRevisionNumberDesc(pid)
                    .map(rev -> rev.getId())
                    .orElse(null)
            );

            // Self-enforce the ADR-0022 invariant that Observation.@PrePersist applies but the native
            // insertIfAbsent path bypasses: severity is an impact band for a BAD observation only, so it
            // must be null unless the assessment is BAD. Idempotent for an already-coerced finding.
            String severityName =
                finding.assessment() == Assessment.BAD && finding.severity() != null ? finding.severity().name() : null;

            int rows = observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                idempotencyKey,
                job.getId(),
                practice.getId(),
                practiceRevisionId,
                artifactType.name(),
                artifactId,
                subjectUserId,
                finding.title(),
                finding.presence().name(),
                finding.assessment() == null ? null : finding.assessment().name(),
                severityName,
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
            // Gate on the assessment, not the insert result: a retry's insertIfAbsent returns 0 for an
            // already-persisted finding, yet hasNegative must still reflect it for the delivery gate.
            if (finding.assessment() == Assessment.BAD) {
                hasNegative = true;
            }
        }

        int totalDiscarded = discardedUnknownSlug + discardedDuplicate + discardedUnresolvedReviewer;
        log.info(
            "Practice detection delivery: inserted={}, unknownSlug={}, duplicate={}, unresolvedReviewer={}, jobId={}",
            inserted,
            discardedUnknownSlug,
            discardedDuplicate,
            discardedUnresolvedReviewer,
            job.getId()
        );

        eventPublisher.publishEvent(
            new PracticeDetectionCompletedEvent(
                job.getId(),
                workspaceId,
                artifactType,
                artifactId,
                // DIVERGENCE (do NOT "simplify" to the per-finding subject): the event's developerId is the
                // ARTIFACT AUTHOR, not the per-row about_user_id. It drives author-side delivery and the mentor
                // cache invalidation for the author. Per-finding rows may be filed against reviewers, but the
                // completion event stays author-scoped.
                authorId,
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
        if (WorkArtifact.CONVERSATION_THREAD.name().equals(artifactType)) {
            // A conversation-review job is repo-less: the subject is a settled Slack thread and the
            // person the finding is about is carried EXPLICITLY in metadata (about_user_id), not resolved
            // from an SCM artifact author. artifactId is the slack_thread aggregate id.
            JsonNode threadIdNode = metadata.get("slack_thread_id");
            if (threadIdNode == null || threadIdNode.isNull() || !threadIdNode.isNumber()) {
                throw new JobDeliveryException("Missing slack_thread_id in job metadata: jobId=" + job.getId());
            }
            JsonNode aboutUserNode = metadata.get("about_user_id");
            if (aboutUserNode == null || aboutUserNode.isNull() || !aboutUserNode.isNumber()) {
                throw new JobDeliveryException("Missing about_user_id in job metadata: jobId=" + job.getId());
            }
            User aboutUser = userRepository
                .findById(aboutUserNode.asLong())
                .orElseThrow(() ->
                    new JobDeliveryException(
                        "Conversation subject user not found: userId=" +
                            aboutUserNode.asLong() +
                            ", jobId=" +
                            job.getId()
                    )
                );
            return new Target(WorkArtifact.CONVERSATION_THREAD, threadIdNode.asLong(), aboutUser);
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
            return new Target(WorkArtifact.ISSUE, issueId, issue.getAuthor());
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
        return new Target(WorkArtifact.PULL_REQUEST, pullRequestId, pullRequest.getAuthor());
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
        Map<ValidatedObservation, String> findingFingerprints
    ) {
        /** Compatibility shape for call sites/tests that do not consume per-finding correlation keys. */
        public DeliveryResult(int inserted, int discardedUnknownSlug, int discardedDuplicate, boolean hasNegative) {
            this(inserted, discardedUnknownSlug, discardedDuplicate, hasNegative, Map.of());
        }
    }
}
