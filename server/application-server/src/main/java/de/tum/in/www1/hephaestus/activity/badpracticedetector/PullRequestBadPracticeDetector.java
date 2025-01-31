package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.activity.PullRequestBadPracticeRepository;
import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.config.BadPracticeDetectorConfig.BadPracticeDetectorService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import java.util.LinkedList;
import java.util.List;

import de.tum.in.www1.hephaestus.intelligenceservice.model.BadPractice;
import de.tum.in.www1.hephaestus.intelligenceservice.model.DetectorRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.DetectorResponse;
import jakarta.transaction.Transactional;
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
    private BadPracticeDetectorService detectorApi;

    public List<PullRequestBadPractice> detectAndSyncBadPractices(PullRequest pullRequest) {
        logger.info("Detecting bad practices for pull request: {}", pullRequest.getId());
        DetectorRequest detectorRequest = new DetectorRequest();
        detectorRequest.setDescription(pullRequest.getBody());
        detectorRequest.setTitle(pullRequest.getTitle());
        DetectorResponse detectorResponse = detectorApi.detectDetectorPost(detectorRequest);

        List<PullRequestBadPractice> detectedBadPractices = new LinkedList<>();

        for (BadPractice badPractice : detectorResponse.getBadPractices()) {
            detectedBadPractices.add(handleDetectedBadPractices(pullRequest, badPractice));
        }

        logger.info("Detected {} bad practices for pull request: {}", detectedBadPractices.size(), pullRequest.getId());
        return detectedBadPractices;
    }

    @Transactional
    protected PullRequestBadPractice handleDetectedBadPractices(PullRequest pullRequest, BadPractice badPractice) {

        PullRequestBadPractice pullRequestBadPractice = new PullRequestBadPractice();
        pullRequestBadPractice.setTitle(badPractice.getTitle());
        pullRequestBadPractice.setDescription(badPractice.getDescription());
        pullRequestBadPractice.setPullrequest(pullRequest);
        pullRequestBadPractice.setResolved(false);
        return pullRequestBadPracticeRepository.save(pullRequestBadPractice);
    }
}
