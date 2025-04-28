package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.activity.PullRequestBadPracticeRepository;
import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPracticeState;
import de.tum.in.www1.hephaestus.activity.model.PullRequestLifecycleState;
import de.tum.in.www1.hephaestus.config.IntelligenceServiceConfig.BadPracticeDetectorService;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.intelligenceservice.model.BadPractice;
import de.tum.in.www1.hephaestus.intelligenceservice.model.DetectorRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.DetectorResponse;
import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PullRequestBadPracticeDetector {

    private static final Logger logger = LoggerFactory.getLogger(PullRequestBadPracticeDetector.class);

    private static final String READY_TO_REVIEW = "ready to review";
    private static final String READY_TO_MERGE = "ready to merge";

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private PullRequestBadPracticeRepository pullRequestBadPracticeRepository;

    @Autowired
    private BadPracticeDetectorService detectorApi;

    /**
     * Detects bad practices in the given pull request and syncs them with the database.
     * @param pullRequest The pull request to detect bad practices in.
     * @return The detected bad practices.
     */
    public List<PullRequestBadPractice> detectAndSyncBadPractices(PullRequest pullRequest) {
        logger.info("Detecting bad practices for pull request: {}", pullRequest.getId());

        List<PullRequestBadPractice> existingBadPractices = pullRequestBadPracticeRepository.findByPullRequestId(
            pullRequest.getId()
        );

        if (
            pullRequest.getUpdatedAt() != null &&
            pullRequest.getLastDetectionTime() != null &&
            pullRequest.getUpdatedAt().isBefore(pullRequest.getLastDetectionTime())
        ) {
            logger.info("Pull request has not been updated since last detection. Skipping detection.");
            return existingBadPractices;
        }

        PullRequestLifecycleState lifecycleState = this.getLifecycleStateOfPullRequest(pullRequest);

        DetectorRequest detectorRequest = new DetectorRequest();
        detectorRequest.setDescription(pullRequest.getBody());
        detectorRequest.setTitle(pullRequest.getTitle());
        detectorRequest.setLifecycleState(lifecycleState.getState());
        if (pullRequest.getBadPracticeSummary() != null) {
            detectorRequest.setBadPracticeSummary(pullRequest.getBadPracticeSummary());
        } else {
            detectorRequest.setBadPracticeSummary("");
        }
        detectorRequest.setBadPractices(
            existingBadPractices.stream().map(this::convertToIntelligenceBadPractice).toList()
        );
        DetectorResponse detectorResponse = detectorApi.detectDetectorPost(detectorRequest);

        pullRequest.setLastDetectionTime(OffsetDateTime.now());
        pullRequest.setBadPracticeSummary(detectorResponse.getBadPracticeSummary());
        pullRequestRepository.save(pullRequest);

        List<PullRequestBadPractice> detectedBadPractices = detectorResponse.getBadPractices()
                .stream()
                .map(badPractice -> {
                    return saveNewDetectedBadPractice(pullRequest, badPractice, lifecycleState);
                }).toList();

        logger.info("Detected {} bad practices for pull request: {}", detectedBadPractices.size(), pullRequest.getId());
        return detectedBadPractices;
    }

    protected PullRequestBadPractice saveNewDetectedBadPractice(PullRequest pullRequest, BadPractice badPractice, PullRequestLifecycleState lifecycleState) {
        PullRequestBadPractice pullRequestBadPractice = new PullRequestBadPractice();
        pullRequestBadPractice.setTitle(badPractice.getTitle());
        pullRequestBadPractice.setDescription(badPractice.getDescription());
        pullRequestBadPractice.setPullrequest(pullRequest);
        pullRequestBadPractice.setState(PullRequestBadPracticeState.fromBadPracticeStatus(badPractice.getStatus()));
        pullRequestBadPractice.setDetectionTime(OffsetDateTime.now());
        pullRequestBadPractice.setLastUpdateTime(OffsetDateTime.now());
        pullRequestBadPractice.setDetectionPullrequestLifecycleState(lifecycleState);
        return pullRequestBadPracticeRepository.save(pullRequestBadPractice);
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
            pullRequest.getLabels().stream().anyMatch(label -> label.getName().equalsIgnoreCase(READY_TO_MERGE))
        ) {
            return PullRequestLifecycleState.READY_TO_MERGE;
        } else if (
            pullRequest.getLabels().stream().anyMatch(label -> label.getName().equalsIgnoreCase(READY_TO_REVIEW))
        ) {
            return PullRequestLifecycleState.READY_TO_REVIEW;
        } else {
            return PullRequestLifecycleState.OPEN;
        }
    }
}
