package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.notification.MailService;
import java.util.List;
import java.util.Set;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class BadPracticeDetectorScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BadPracticeDetectorScheduler.class);

    private static final String READY_TO_REVIEW = "ready to review";
    private static final String READY_TO_MERGE = "ready to merge";

    @Qualifier("applicationTaskExecutor")
    @Autowired
    TaskExecutor taskExecutor;

    @Autowired
    PullRequestBadPracticeDetector pullRequestBadPracticeDetector;

    @Autowired
    MailService mailService;

    @Autowired
    Keycloak keycloak;

    @Value("${hephaestus.detection.run-automatic-detection-for-all}")
    private boolean runAutomaticDetectionForAll;

    @Value("${keycloak.realm}")
    private String realm;

    public void detectBadPracticeForPrIfReady(PullRequest pullRequest, Set<Label> oldLabels, Set<Label> newLabels) {
        if (
                (newLabels.stream().anyMatch(label -> READY_TO_REVIEW.equals(label.getName())) &&
                        oldLabels.stream().noneMatch(label -> READY_TO_REVIEW.equals(label.getName()))) ||
                        (newLabels.stream().anyMatch(label -> READY_TO_MERGE.equals(label.getName())) &&
                                oldLabels.stream().noneMatch(label -> READY_TO_MERGE.equals(label.getName())))
        ) {
            if (runAutomaticDetectionForAll) {
                scheduleDetection(pullRequest);
            } else {
                checkUserRoleAndScheduleDetection(pullRequest);
            }
        }
    }

    public void scheduleDetection(PullRequest pullRequest) {
        logger.info("Scheduling bad practice detection for pull request: {}", pullRequest.getId());
        BadPracticeDetectorTask badPracticeDetectorTask = new BadPracticeDetectorTask();
        badPracticeDetectorTask.setPullRequestBadPracticeDetector(pullRequestBadPracticeDetector);
        badPracticeDetectorTask.setMailService(mailService);
        badPracticeDetectorTask.setPullRequest(pullRequest);
        taskExecutor.execute(badPracticeDetectorTask);
    }

    public void checkUserRoleAndScheduleDetection(PullRequest pullRequest) {
        User assignee = pullRequest.getAssignees().stream().findFirst().orElse(null);

        if (assignee == null) {
            return;
        }

        try {
            UserRepresentation keyCloakUser = keycloak
                    .realm(realm)
                    .users()
                    .searchByUsername(assignee.getLogin(), true)
                    .getFirst();

            List<RoleRepresentation> roles = keycloak
                    .realm(realm)
                    .users()
                    .get(keyCloakUser.getId())
                    .roles()
                    .realmLevel()
                    .listAll();

            boolean hasRunAutomaticDetection = roles
                    .stream()
                    .anyMatch(role -> "run_automatic_detection".equals(role.getName()));
            if (!hasRunAutomaticDetection) {
                logger.info(
                        "User {} does not have the run_automatic_detection role. Skipping email.",
                        assignee.getLogin()
                );
            } else {
                logger.info("User {} has the run_automatic_detection role. Scheduling detection.", assignee.getLogin());
                scheduleDetection(pullRequest);
            }
        } catch (Exception e) {
            logger.error("Failed to find user in Keycloak: {}", assignee.getLogin());
        }
    }
}
