package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.notification.MailService;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class BadPracticeDetectorTask implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(BadPracticeDetectorTask.class);

    @Autowired
    private PullRequestBadPracticeDetector pullRequestBadPracticeDetector;

    @Autowired
    private MailService mailService;

    private PullRequest pullRequest;

    @Override
    public void run() {
        List<PullRequestBadPractice> badPractices = pullRequestBadPracticeDetector.detectAndSyncBadPractices(
            pullRequest
        );
        logger.info("Bad practices detected in pull request: {}", pullRequest.getId());
        logger.info("Bad practices: {}", badPractices);

        if (!badPractices.isEmpty()) {
            for (User user : pullRequest.getAssignees()) {
                mailService.sendBadPracticesDetectedInPullRequestEmail(user, pullRequest, badPractices);
            }
        }
    }
}
