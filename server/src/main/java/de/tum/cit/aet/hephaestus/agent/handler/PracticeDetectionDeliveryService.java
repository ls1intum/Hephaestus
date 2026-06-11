package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeDetectionCompletedEvent;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeFindingTargetType;
import de.tum.cit.aet.hephaestus.practices.model.Verdict;
import java.time.Instant;
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
    private final PracticeFindingRepository practiceFindingRepository;
    private final PullRequestRepository pullRequestRepository;
    private final IssueRepository issueRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public PracticeDetectionDeliveryService(
        PracticeRepository practiceRepository,
        PracticeFindingRepository practiceFindingRepository,
        PullRequestRepository pullRequestRepository,
        IssueRepository issueRepository,
        ApplicationEventPublisher eventPublisher,
        ObjectMapper objectMapper
    ) {
        this.practiceRepository = practiceRepository;
        this.practiceFindingRepository = practiceFindingRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.issueRepository = issueRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /** Resolved delivery target: who the finding is about + the typed (kind, id) reference. */
    private record Target(PracticeFindingTargetType type, Long id, Long contributorId) {}

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
            return new DeliveryResult(0, validFindings.size(), 0, false);
        }

        // Resolve the typed target + the contributor the finding is about, routing on the artifact.
        Target target = resolveTarget(job, metadata);
        Long contributorId = target.contributorId();
        PracticeFindingTargetType targetType = target.type();
        Long targetId = target.id();

        // Persist findings
        int inserted = 0;
        int discardedUnknownSlug = 0;
        int discardedDuplicate = 0;
        boolean hasNegative = false;
        Instant detectedAt = Instant.now();

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
                finding.practiceSlug() + ":" + i + ":" + targetType.name() + ":" + targetId + ":" + job.getId();

            // Serialize evidence
            String evidenceJson = null;
            if (finding.evidence() != null) {
                try {
                    evidenceJson = objectMapper.writeValueAsString(finding.evidence());
                } catch (JacksonException e) {
                    log.debug("Failed to serialize evidence, storing null: jobId={}", job.getId());
                }
            }

            // Insert (idempotent)
            int rows = practiceFindingRepository.insertIfAbsent(
                UUID.randomUUID(),
                idempotencyKey,
                job.getId(),
                practice.getId(),
                targetType.name(),
                targetId,
                contributorId,
                finding.title(),
                finding.verdict().name(),
                finding.severity().name(),
                finding.confidence(),
                evidenceJson,
                finding.reasoning(),
                finding.guidance(),
                detectedAt
            );

            if (rows == 1) {
                inserted++;
            } else {
                discardedDuplicate++;
            }
            // Track negative findings based on verdict, not insert result.
            // Critical for retry delivery: on retry, insertIfAbsent returns 0 for existing
            // findings, but we still need correct hasNegative for the delivery gate.
            if (finding.verdict() == Verdict.NEGATIVE) {
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
                targetType,
                targetId,
                contributorId,
                inserted,
                totalDiscarded,
                hasNegative
            )
        );

        return new DeliveryResult(inserted, discardedUnknownSlug, discardedDuplicate, hasNegative);
    }

    /**
     * Route the delivery target on the job's artifact. Issue jobs carry {@code target_type=ISSUE} +
     * {@code issue_id}; PR jobs carry {@code pull_request_id} (no {@code target_type} → defaults to PR,
     * keeping existing PR jobs and replays working).
     */
    private Target resolveTarget(AgentJob job, JsonNode metadata) {
        String targetType = metadata.has("target_type") ? metadata.get("target_type").asString() : "PULL_REQUEST";
        if (PracticeFindingTargetType.ISSUE.name().equals(targetType)) {
            if (!metadata.has("issue_id")) {
                throw new JobDeliveryException("Missing issue_id in job metadata: jobId=" + job.getId());
            }
            Long issueId = metadata.get("issue_id").asLong();
            // TYPE(i)=Issue finder: never resolve a PullRequest under an ISSUE target_type (shared table/id space).
            Issue issue = issueRepository
                .findByIdWithRepository(issueId)
                .orElseThrow(() ->
                    new JobDeliveryException("Issue not found: issueId=" + issueId + ", jobId=" + job.getId())
                );
            if (issue.getAuthor() == null) {
                throw new JobDeliveryException("Issue has no author: issueId=" + issueId + ", jobId=" + job.getId());
            }
            return new Target(PracticeFindingTargetType.ISSUE, issueId, issue.getAuthor().getId());
        }
        if (!metadata.has("pull_request_id")) {
            throw new JobDeliveryException("Missing pull_request_id in job metadata: jobId=" + job.getId());
        }
        Long pullRequestId = metadata.get("pull_request_id").asLong();
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
        return new Target(PracticeFindingTargetType.PULL_REQUEST, pullRequestId, pullRequest.getAuthor().getId());
    }

    public record DeliveryResult(int inserted, int discardedUnknownSlug, int discardedDuplicate, boolean hasNegative) {}
}
