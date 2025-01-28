package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.activity.PullRequestBadPracticeRepository;
import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.intelligenceservice.api.DetectorApi;
import de.tum.in.www1.hephaestus.intelligenceservice.model.DetectorRequest;
import de.tum.in.www1.hephaestus.intelligenceservice.model.DetectorResponse;
import de.tum.in.www1.hephaestus.intelligenceservice.model.PullRequestWithBadPractices;
import de.tum.in.www1.hephaestus.intelligenceservice.model.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PullRequestBadPracticeDetector {

    private static final Logger logger = LoggerFactory.getLogger(PullRequestBadPracticeDetector.class);

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private PullRequestBadPracticeRepository pullRequestBadPracticeRepository;

    private final DetectorApi detectorApi = new DetectorApi();

    public List<PullRequestBadPractice> detectAndSyncBadPractices(List<PullRequest> pullRequests) {
        logger.info("Detecting bad practices for pull requests.");


        DetectorRequest detectorRequest = new DetectorRequest();
        detectorRequest.setPullRequests(mapToApiPullRequests(pullRequests));
        DetectorResponse detectorResponse = detectorApi.detectDetectorPost(detectorRequest);

        List<PullRequestBadPractice> detectedBadPractices = new LinkedList<>();

        detectorResponse.getDetectBadPractices().forEach(prWithBadPractices ->
            detectedBadPractices.addAll(handleDetectedBadPractices(prWithBadPractices))
        );

        return detectedBadPractices;
    }

    private List<PullRequestBadPractice> handleDetectedBadPractices(PullRequestWithBadPractices prWithBadPractices) {

        Long pullRequestId = Long.valueOf(prWithBadPractices.getPullRequestId());
        PullRequest pullRequest = pullRequestRepository.findById(pullRequestId).orElse(null);

        if (pullRequest == null) {
            logger.error("Pull request with id {} not found.", prWithBadPractices.getPullRequestId());
            return List.of();
        }

        List<PullRequestBadPractice> existingBadPractices = pullRequestBadPracticeRepository.findByPullRequestId(
                pullRequest.getId()
        );

        List<PullRequestBadPractice> newBadPractices = prWithBadPractices.getBadPracticeIds().stream()
                .map(badPracticeRule -> {
                    PullRequestBadPractice pullRequestBadPractice = new PullRequestBadPractice();
                    pullRequestBadPractice.setPullrequest(pullRequest);
                    pullRequestBadPractice.setResolved(false);
                    return pullRequestBadPractice;
                })
                .collect(Collectors.toList());

        pullRequestBadPracticeRepository.saveAll(newBadPractices);
        existingBadPractices.removeAll(newBadPractices);
        existingBadPractices.forEach(badPractice -> badPractice.setResolved(true));
        pullRequestBadPracticeRepository.saveAll(existingBadPractices);
        return newBadPractices;
    }

    private BadPracticeType findBadPracticeType(String badPracticeRuleId) {
        return pullRequestBadPracticeRuleRepository.findById(Long.valueOf(badPracticeRuleId))
                .map(PullRequestBadPracticeRule::getType)
                .orElse(null);
    }

    private List<de.tum.in.www1.hephaestus.intelligenceservice.model.PullRequest> mapToApiPullRequests(List<PullRequest> pullRequests) {
        return pullRequests.stream().map(pullRequest -> {
            de.tum.in.www1.hephaestus.intelligenceservice.model.PullRequest apiPullRequest = new de.tum.in.www1.hephaestus.intelligenceservice.model.PullRequest();
            apiPullRequest.setId(String.valueOf(pullRequest.getId()));
            apiPullRequest.setTitle(pullRequest.getTitle());
            apiPullRequest.setDescription(pullRequest.getBody());
            return apiPullRequest;
        }).collect(Collectors.toList());
    }

    private List<Rule> mapToApiRules(List<PullRequestBadPracticeRule> rules) {
        return rules.stream().map(rule -> {
            Rule apiRule = new Rule();
            apiRule.setBadPracticeId(String.valueOf(rule.getId()));
            apiRule.setName(rule.getType().getTitle());
            apiRule.setDescription(rule.getType().getLlmPrompt());
            return apiRule;
        }).collect(Collectors.toList());
    }
}
