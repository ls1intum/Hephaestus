package de.tum.in.www1.hephaestus.practices.detection;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.intelligenceservice.api.DetectorApi;
import de.tum.in.www1.hephaestus.intelligenceservice.model.BadPractice;
import de.tum.in.www1.hephaestus.intelligenceservice.model.DetectorRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.DetectorResponse;
import de.tum.in.www1.hephaestus.practices.detection.BadPracticeDetectionRepository;
import de.tum.in.www1.hephaestus.practices.model.BadPracticeDetection;
import de.tum.in.www1.hephaestus.practices.model.DetectionResult;
import de.tum.in.www1.hephaestus.practices.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.practices.model.PullRequestBadPracticeState;
import de.tum.in.www1.hephaestus.practices.model.PullRequestLifecycleState;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * Service for detecting bad practices in pull requests.
 *
 * <p>Analyzes PR metadata (title, description, labels) against the PR template
 * and uses the intelligence service to identify potential issues.
 */
@Component
public class PullRequestBadPracticeDetector {

    private static final Logger logger = LoggerFactory.getLogger(PullRequestBadPracticeDetector.class);

    private static final String READY_TO_REVIEW = "ready to review";
    private static final String READY_FOR_REVIEW = "ready for review";
    private static final String READY_TO_MERGE = "ready to merge";

    private final PullRequestRepository pullRequestRepository;

    private final BadPracticeDetectionRepository badPracticeDetectionRepository;

    private final PullRequestTemplateGetter pullRequestTemplateGetter;

    private final DetectorApi detectorApi;

    public PullRequestBadPracticeDetector(
        PullRequestRepository pullRequestRepository,
        BadPracticeDetectionRepository badPracticeDetectionRepository,
        PullRequestTemplateGetter pullRequestTemplateGetter,
        DetectorApi detectorApi
    ) {
        this.pullRequestRepository = pullRequestRepository;
        this.badPracticeDetectionRepository = badPracticeDetectionRepository;
        this.pullRequestTemplateGetter = pullRequestTemplateGetter;
        this.detectorApi = detectorApi;
    }

    /**
     * Detects bad practices for all open pull requests assigned to a user in a workspace.
     *
     * @param workspaceId The workspace ID to scope the search.
     * @param login The user's login to find assigned pull requests.
     * @return The aggregate detection result.
     */
    @Transactional
    public DetectionResult detectForUser(Long workspaceId, String login) {
        logger.info("Detecting bad practices for user {} in workspace {}", login, workspaceId);

        List<PullRequest> pullRequests = pullRequestRepository.findAssignedByLoginAndStates(
            login,
            java.util.Set.of(de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State.OPEN),
            workspaceId
        );

        List<DetectionResult> results = pullRequests.stream().map(this::detectAndSyncBadPractices).toList();

        if (results.stream().anyMatch(r -> r == DetectionResult.BAD_PRACTICES_DETECTED)) {
            return DetectionResult.BAD_PRACTICES_DETECTED;
        } else if (results.stream().anyMatch(r -> r == DetectionResult.NO_BAD_PRACTICES_DETECTED)) {
            return DetectionResult.NO_BAD_PRACTICES_DETECTED;
        } else {
            return DetectionResult.ERROR_NO_UPDATE_ON_PULLREQUEST;
        }
    }

    /**
     * Detects bad practices for a given pull request ID and syncs the results with the
     * database. This method fetches the pull request entity from the database.
     *
     * @param pullRequestId The ID of the pull request to detect bad practices for.
     * @return The detection result, or ERROR_NO_UPDATE_ON_PULLREQUEST if the PR is not found.
     */
    @Transactional
    public DetectionResult detectAndSyncBadPractices(Long pullRequestId) {
        logger.debug("Looking up pull request by ID: {}", pullRequestId);
        return pullRequestRepository
            .findById(pullRequestId)
            .map(this::detectAndSyncBadPractices)
            .orElseGet(() -> {
                logger.warn("Pull request with ID {} not found", pullRequestId);
                return DetectionResult.ERROR_NO_UPDATE_ON_PULLREQUEST;
            });
    }

    /**
     * Detects bad practices for a given pull request and syncs the results with the
     * database.
     *
     * @param pullRequest The pull request to detect bad practices for.
     * @return The detection result.
     */
    @Transactional
    public DetectionResult detectAndSyncBadPractices(PullRequest pullRequest) {
        logger.info("Detecting bad practices for pull request: {}", pullRequest.getId());

        if (
            pullRequest.getUpdatedAt() != null &&
            pullRequest.getLastDetectionTime() != null &&
            pullRequest.getUpdatedAt().isBefore(pullRequest.getLastDetectionTime())
        ) {
            logger.info("Pull request has not been updated since last detection. Skipping detection.");
            return DetectionResult.ERROR_NO_UPDATE_ON_PULLREQUEST;
        }

        BadPracticeDetection detection = detectBadPracticesForPullRequest(pullRequest);

        if (detection.getBadPractices().isEmpty()) {
            return DetectionResult.NO_BAD_PRACTICES_DETECTED;
        } else {
            return DetectionResult.BAD_PRACTICES_DETECTED;
        }
    }

    /**
     * Detects bad practices for a given pull request.
     *
     * <p>Protected by a circuit breaker to prevent cascading failures when the
     * intelligence service is unavailable. When the circuit is open, returns
     * an empty detection result.
     *
     * @param pullRequest The pull request to detect bad practices for.
     * @return The detection result.
     */
    @Transactional
    @CircuitBreaker(name = "intelligence-service", fallbackMethod = "fallbackDetection")
    public BadPracticeDetection detectBadPracticesForPullRequest(PullRequest pullRequest) {
        BadPracticeDetection lastDetection = badPracticeDetectionRepository.findMostRecentByPullRequestId(
            pullRequest.getId()
        );

        String summary;
        List<PullRequestBadPractice> existingBadPractices;

        if (lastDetection == null) {
            summary = "";
            existingBadPractices = List.of();
        } else {
            summary = lastDetection.getSummary();
            existingBadPractices = lastDetection.getBadPractices();
        }

        PullRequestLifecycleState lifecycleState = this.getLifecycleStateOfPullRequest(pullRequest);
        String template = pullRequestTemplateGetter.getPullRequestTemplate(
            pullRequest.getRepository().getNameWithOwner()
        );

        DetectorRequest detectorRequest = new DetectorRequest();
        detectorRequest.setDescription(pullRequest.getBody());
        detectorRequest.setTitle(pullRequest.getTitle());
        detectorRequest.setLifecycleState(lifecycleState.getState());
        detectorRequest.setRepositoryName(pullRequest.getRepository().getName());
        detectorRequest.setPullRequestNumber(BigDecimal.valueOf(pullRequest.getNumber()));
        detectorRequest.setBadPracticeSummary(summary);
        detectorRequest.setBadPractices(
            existingBadPractices.stream().map(this::convertToIntelligenceBadPractice).toList()
        );
        detectorRequest.setPullRequestTemplate(template);

        DetectorResponse detectorResponse;
        try {
            detectorResponse = detectorApi.detectBadPractices(detectorRequest);
        } catch (RestClientException e) {
            logger.error(
                "Failed to detect bad practices for pull request {} (PR #{} in {}): {}",
                pullRequest.getId(),
                pullRequest.getNumber(),
                pullRequest.getRepository().getNameWithOwner(),
                e.getMessage()
            );
            // Return empty detection to prevent transaction issues
            BadPracticeDetection emptyDetection = new BadPracticeDetection();
            emptyDetection.setPullRequest(pullRequest);
            emptyDetection.setBadPractices(Collections.emptyList());
            emptyDetection.setSummary("");
            emptyDetection.setDetectionTime(Instant.now());
            emptyDetection.setTraceId(null);
            return emptyDetection;
        }

        pullRequest.setLastDetectionTime(Instant.now());
        pullRequest.setBadPracticeSummary(detectorResponse.getBadPracticeSummary());
        pullRequestRepository.save(pullRequest);

        List<PullRequestBadPractice> detectedBadPractices = detectorResponse
            .getBadPractices()
            .stream()
            .map(badPractice ->
                newDetectedBadPractice(
                    pullRequest,
                    badPractice,
                    lifecycleState,
                    detectorResponse.getTraceId(),
                    existingBadPractices
                )
            )
            .toList();

        logger.info("Detected {} bad practices for pull request: {}", detectedBadPractices.size(), pullRequest.getId());

        BadPracticeDetection badPracticeDetection = new BadPracticeDetection();
        badPracticeDetection.setPullRequest(pullRequest);
        badPracticeDetection.setBadPractices(detectedBadPractices);
        badPracticeDetection.setSummary(detectorResponse.getBadPracticeSummary());
        badPracticeDetection.setDetectionTime(Instant.now());
        badPracticeDetection.setTraceId(detectorResponse.getTraceId());

        detectedBadPractices.forEach(badPractice -> {
            badPractice.setBadPracticeDetection(badPracticeDetection);
        });

        return badPracticeDetectionRepository.save(badPracticeDetection);
    }

    protected PullRequestBadPractice newDetectedBadPractice(
        PullRequest pullRequest,
        BadPractice badPractice,
        PullRequestLifecycleState lifecycleState,
        String traceId,
        List<PullRequestBadPractice> existingBadPractices
    ) {
        PullRequestBadPractice pullRequestBadPractice = new PullRequestBadPractice();

        existingBadPractices
            .stream()
            .filter(existing -> existing.getTitle().equals(badPractice.getTitle()))
            .findFirst()
            .ifPresent(existingBadPractice -> pullRequestBadPractice.setUserState(existingBadPractice.getUserState()));

        pullRequestBadPractice.setTitle(badPractice.getTitle());
        pullRequestBadPractice.setDescription(badPractice.getDescription());
        pullRequestBadPractice.setPullrequest(pullRequest);
        pullRequestBadPractice.setState(PullRequestBadPracticeState.fromBadPracticeStatus(badPractice.getStatus()));
        pullRequestBadPractice.setDetectionTime(Instant.now());
        pullRequestBadPractice.setLastUpdateTime(Instant.now());
        pullRequestBadPractice.setDetectionPullrequestLifecycleState(lifecycleState);
        pullRequestBadPractice.setDetectionTraceId(traceId);
        return pullRequestBadPractice;
    }

    private BadPractice convertToIntelligenceBadPractice(PullRequestBadPractice pullRequestBadPractice) {
        BadPractice badPractice = new BadPractice();
        badPractice.setTitle(pullRequestBadPractice.getTitle());
        badPractice.setDescription(pullRequestBadPractice.getDescription());
        badPractice.setStatus(PullRequestBadPracticeState.toBadPracticeStatus(pullRequestBadPractice.getState()));
        return badPractice;
    }

    private PullRequestLifecycleState getLifecycleStateOfPullRequest(PullRequest pullRequest) {
        if (pullRequest.isMerged()) {
            return PullRequestLifecycleState.MERGED;
        } else if (pullRequest.getState() == Issue.State.CLOSED) {
            return PullRequestLifecycleState.CLOSED;
        } else if (pullRequest.isDraft()) {
            return PullRequestLifecycleState.DRAFT;
        } else if (
            pullRequest
                .getLabels()
                .stream()
                .anyMatch(label -> label.getName().equalsIgnoreCase(READY_TO_MERGE))
        ) {
            return PullRequestLifecycleState.READY_TO_MERGE;
        } else if (
            pullRequest
                .getLabels()
                .stream()
                .anyMatch(label -> label.getName().equalsIgnoreCase(READY_TO_REVIEW)) ||
            pullRequest
                .getLabels()
                .stream()
                .anyMatch(label -> label.getName().equalsIgnoreCase(READY_FOR_REVIEW))
        ) {
            return PullRequestLifecycleState.READY_FOR_REVIEW;
        } else {
            return PullRequestLifecycleState.OPEN;
        }
    }

    /**
     * Fallback method when circuit breaker is open or intelligence service call fails.
     *
     * @param pullRequest the pull request that was being analyzed
     * @param throwable   the cause of the fallback
     * @return empty detection result allowing the system to continue operating
     */
    @SuppressWarnings("unused") // Used by Resilience4j via reflection
    private BadPracticeDetection fallbackDetection(PullRequest pullRequest, Throwable throwable) {
        logger.warn(
            "Circuit breaker triggered for PR {} ({}): {}",
            pullRequest.getId(),
            pullRequest.getRepository().getNameWithOwner(),
            throwable.getMessage()
        );

        BadPracticeDetection emptyDetection = new BadPracticeDetection();
        emptyDetection.setPullRequest(pullRequest);
        emptyDetection.setBadPractices(Collections.emptyList());
        emptyDetection.setSummary("");
        emptyDetection.setDetectionTime(Instant.now());
        emptyDetection.setTraceId("fallback-" + UUID.randomUUID());
        return emptyDetection;
    }
}
