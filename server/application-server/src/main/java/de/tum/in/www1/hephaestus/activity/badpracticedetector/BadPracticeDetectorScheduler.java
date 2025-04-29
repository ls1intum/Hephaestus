package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.notification.MailService;
import java.time.Instant;
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
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class BadPracticeDetectorScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BadPracticeDetectorScheduler.class);

    private static final String READY_TO_REVIEW = "ready to review";
    private static final String READY_TO_MERGE = "ready to merge";

    @Qualifier("taskScheduler")
    @Autowired
    TaskScheduler taskScheduler;

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

    public void detectBadPracticeForPrWhenOpenedOrReadyForReviewEvent(PullRequest pullRequest) {
        Instant timeInOneHour = Instant.now().plusSeconds(3600);
        runAutomaticDetectionForAllIfEnabled(pullRequest, timeInOneHour);
    }

    public void detectBadPracticeForPrIfReadyLabels(
        PullRequest pullRequest,
        Set<Label> oldLabels,
        Set<Label> newLabels
    ) {
        if (
            (newLabels.stream().anyMatch(label -> READY_TO_REVIEW.equals(label.getName())) &&
                oldLabels.stream().noneMatch(label -> READY_TO_REVIEW.equals(label.getName()))) ||
            (newLabels.stream().anyMatch(label -> READY_TO_MERGE.equals(label.getName())) &&
                oldLabels.stream().noneMatch(label -> READY_TO_MERGE.equals(label.getName())))
        ) {
            runAutomaticDetectionForAllIfEnabled(pullRequest, Instant.now());
        }
    }

    private void runAutomaticDetectionForAllIfEnabled(PullRequest pullRequest, Instant scheduledTime) {
        if (runAutomaticDetectionForAll) {
            scheduleDetectionAtTime(pullRequest, scheduledTime);
        } else {
            checkUserRoleAndScheduleDetectionAtTime(pullRequest, scheduledTime);
        }
    }

    private void checkUserRoleAndScheduleDetectionAtTime(PullRequest pullRequest, Instant scheduledTime) {
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
                scheduleDetectionAtTime(pullRequest, scheduledTime);
            }
        } catch (Exception e) {
            logger.error("Failed to find user in Keycloak: {}", assignee.getLogin());
        }
    }

    private void scheduleDetectionAtTime(PullRequest pullRequest, Instant scheduledTime) {
        logger.info(
            "Scheduling bad practice detection for pull request: {} at time {}",
            pullRequest.getId(),
            scheduledTime
        );
        BadPracticeDetectorTask badPracticeDetectorTask = new BadPracticeDetectorTask();
        badPracticeDetectorTask.setPullRequestBadPracticeDetector(pullRequestBadPracticeDetector);
        badPracticeDetectorTask.setMailService(mailService);
        badPracticeDetectorTask.setPullRequest(pullRequest);
        taskScheduler.schedule(badPracticeDetectorTask, scheduledTime);
    }
}
