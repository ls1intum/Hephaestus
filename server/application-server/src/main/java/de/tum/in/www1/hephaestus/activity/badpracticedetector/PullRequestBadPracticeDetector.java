package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.activity.PullRequestBadPracticeRepository;
import de.tum.in.www1.hephaestus.activity.badpracticedetector.guideline.PullRequestBadPracticeGuideline;
import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPracticeType;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.leaderboard.LeaderboardService;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PullRequestBadPracticeDetector {

    private static final Logger logger = LoggerFactory.getLogger(PullRequestBadPracticeDetector.class);

    @Autowired
    private PullRequestBadPracticeRepository pullRequestBadPracticeRepository;

    @Autowired
    private Set<PullRequestBadPracticeGuideline> pullRequestBadPracticeGuidelines;

    public List<PullRequestBadPractice> detectAndSyncBadPractices(PullRequest pullRequest) {
        logger.info(
            "Detecting bad practices for pull request: {} using guidelines: {}",
            pullRequest.getId(),
            pullRequestBadPracticeGuidelines
        );

        List<PullRequestBadPractice> existingBadPractices = pullRequestBadPracticeRepository.findByPullRequestId(
            pullRequest.getId()
        );

        List<PullRequestBadPractice> newBadPractices = pullRequestBadPracticeGuidelines
            .stream()
            .map(guideline -> guideline.detectBadPractices(pullRequest))
            .flatMap(List::stream)
            .toList();

        updateExistingBadPractices(existingBadPractices, newBadPractices);

        return saveBadPractices(existingBadPractices, newBadPractices);
    }

    private List<PullRequestBadPractice> saveBadPractices(
        List<PullRequestBadPractice> existingBadPractices,
        List<PullRequestBadPractice> detectedBadPractices
    ) {
        List<PullRequestBadPracticeType> types = detectedBadPractices
            .stream()
            .map(PullRequestBadPractice::getType)
            .toList();

        List<PullRequestBadPractice> newBadPractices = detectedBadPractices
            .stream()
            .filter(badPractice -> !existingBadPractices.contains(badPractice))
            .toList();

        return pullRequestBadPracticeRepository.saveAll(newBadPractices);
    }

    private void updateExistingBadPractices(
        List<PullRequestBadPractice> existingBadPractices,
        List<PullRequestBadPractice> detectedBadPractices
    ) {
        List<PullRequestBadPracticeType> types = detectedBadPractices
            .stream()
            .map(PullRequestBadPractice::getType)
            .toList();

        for (PullRequestBadPractice existingBadPractice : existingBadPractices) {
            if (!types.contains(existingBadPractice.getType())) {
                logger.info("Resolving bad practice: {}", existingBadPractice.getType());
                existingBadPractice.setResolved(true);
                pullRequestBadPracticeRepository.save(existingBadPractice);
            }
        }
    }
}
