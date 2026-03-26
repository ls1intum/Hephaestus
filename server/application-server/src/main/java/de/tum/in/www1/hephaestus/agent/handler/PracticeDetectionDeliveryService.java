package de.tum.in.www1.hephaestus.agent.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.practices.finding.PracticeDetectionCompletedEvent;
import de.tum.in.www1.hephaestus.practices.finding.PracticeDetectionProperties;
import de.tum.in.www1.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.in.www1.hephaestus.practices.model.Practice;
import de.tum.in.www1.hephaestus.practices.model.PracticeFindingTargetType;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PracticeDetectionProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public PracticeDetectionDeliveryService(
        PracticeRepository practiceRepository,
        PracticeFindingRepository practiceFindingRepository,
        PullRequestRepository pullRequestRepository,
        PracticeDetectionProperties properties,
        ApplicationEventPublisher eventPublisher,
        ObjectMapper objectMapper
    ) {
        this.practiceRepository = practiceRepository;
        this.practiceFindingRepository = practiceFindingRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

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
        if (metadata == null || !metadata.has("pull_request_id")) {
            throw new JobDeliveryException("Missing pull_request_id in job metadata: jobId=" + job.getId());
        }
        Long pullRequestId = metadata.get("pull_request_id").asLong();

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
            return new DeliveryResult(0, validFindings.size(), 0, 0, false);
        }

        // Resolve target PR and author
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
        Long contributorId = pullRequest.getAuthor().getId();
        PracticeFindingTargetType targetType = PracticeFindingTargetType.PULL_REQUEST;
        Long targetId = pullRequestId;

        // Persist findings
        Map<String, Integer> negativeCounts = new HashMap<>();
        int inserted = 0;
        int discardedUnknownSlug = 0;
        int discardedOverCap = 0;
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

            // Cap negatives per practice
            if (finding.verdict() == Verdict.NEGATIVE) {
                int count = negativeCounts.getOrDefault(finding.practiceSlug(), 0);
                if (count >= properties.maxNegativeFindingsPerPractice()) {
                    discardedOverCap++;
                    log.info(
                        "Discarded finding over negative cap: slug={}, cap={}, jobId={}",
                        finding.practiceSlug(),
                        properties.maxNegativeFindingsPerPractice(),
                        job.getId()
                    );
                    continue;
                }
                negativeCounts.put(finding.practiceSlug(), count + 1);
            }

            // Build idempotency key — index disambiguates multiple findings for the same practice
            String idempotencyKey =
                finding.practiceSlug() + ":" + targetType.name() + ":" + targetId + ":" + job.getId() + ":" + i;

            // Serialize evidence
            String evidenceJson = null;
            if (finding.evidence() != null) {
                try {
                    evidenceJson = objectMapper.writeValueAsString(finding.evidence());
                } catch (JsonProcessingException e) {
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
                finding.guidanceMethod() != null ? finding.guidanceMethod().name() : null,
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

        int totalDiscarded = discardedUnknownSlug + discardedOverCap + discardedDuplicate;
        log.info(
            "Practice detection delivery: inserted={}, unknownSlug={}, overCap={}, duplicate={}, jobId={}",
            inserted,
            discardedUnknownSlug,
            discardedOverCap,
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

        return new DeliveryResult(inserted, discardedUnknownSlug, discardedOverCap, discardedDuplicate, hasNegative);
    }

    public record DeliveryResult(
        int inserted,
        int discardedUnknownSlug,
        int discardedOverCap,
        int discardedDuplicate,
        boolean hasNegative
    ) {}
}
