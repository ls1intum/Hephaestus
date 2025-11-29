package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.notification.MailService;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.ProcessingException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BadPracticeDetectorScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BadPracticeDetectorScheduler.class);

    private static final String READY_TO_REVIEW = "ready to review";
    private static final String READY_FOR_REVIEW = "ready for review";
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

    private final Map<Long, List<ScheduledFuture<?>>> scheduledTasks = new HashMap<>();
    private final AtomicBoolean keycloakRoleChecksHealthy = new AtomicBoolean(true);

    public void detectBadPracticeForPrWhenOpenedOrReadyForReviewEvent(PullRequest pullRequest) {
        Instant timeInOneHour = Instant.now().plusSeconds(3600);
        runAutomaticDetectionForAllIfEnabled(pullRequest, timeInOneHour, true);
    }

    public void detectBadPracticeForPrIfReadyLabels(
        PullRequest pullRequest,
        Set<Label> oldLabels,
        Set<Label> newLabels
    ) {
        if (
            (newLabels.stream().anyMatch(label -> READY_TO_REVIEW.equals(label.getName())) &&
                oldLabels.stream().noneMatch(label -> READY_TO_REVIEW.equals(label.getName()))) ||
            (newLabels.stream().anyMatch(label -> READY_FOR_REVIEW.equals(label.getName())) &&
                oldLabels.stream().noneMatch(label -> READY_FOR_REVIEW.equals(label.getName()))) ||
            (newLabels.stream().anyMatch(label -> READY_TO_MERGE.equals(label.getName())) &&
                oldLabels.stream().noneMatch(label -> READY_TO_MERGE.equals(label.getName())))
        ) {
            runAutomaticDetectionForAllIfEnabled(pullRequest, Instant.now(), true);
        }
    }

    public void detectBadPracticeForPrIfClosedEvent(PullRequest pullRequest) {
        runAutomaticDetectionForAllIfEnabled(pullRequest, Instant.now(), false);
    }

    private void runAutomaticDetectionForAllIfEnabled(
        PullRequest pullRequest,
        Instant scheduledTime,
        boolean sendBadPracticeDetectionEmail
    ) {
        if (runAutomaticDetectionForAll) {
            scheduleDetectionAtTime(pullRequest, scheduledTime, sendBadPracticeDetectionEmail);
        } else {
            checkUserRoleAndScheduleDetectionAtTime(pullRequest, scheduledTime, sendBadPracticeDetectionEmail);
        }
    }

    private void checkUserRoleAndScheduleDetectionAtTime(
        PullRequest pullRequest,
        Instant scheduledTime,
        boolean sendBadPracticeDetectionEmail
    ) {
        User assignee = pullRequest.getAssignees().stream().findFirst().orElse(null);

        if (assignee == null) {
            return;
        }

        if (!keycloakRoleChecksHealthy.get()) {
            logger.debug(
                "Skipping Keycloak role check for PR {} because the Keycloak client is marked unhealthy",
                pullRequest.getId()
            );
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
                scheduleDetectionAtTime(pullRequest, scheduledTime, sendBadPracticeDetectionEmail);
            }
        } catch (ProcessingException | NotAuthorizedException e) {
            if (keycloakRoleChecksHealthy.compareAndSet(true, false)) {
                logger.error(
                    "Disabling automatic detection role checks after Keycloak responded with {}. " +
                    "Set KEYCLOAK credentials or restart the server to re-enable.",
                    e.getMessage()
                );
            } else {
                logger.debug("Keycloak role check skipped due to previous authorization failure: {}", e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error while checking user role: {}", e.toString());
        }
    }

    private void scheduleDetectionAtTime(
        PullRequest pullRequest,
        Instant scheduledTime,
        boolean sendBadPracticeDetectionEmail
    ) {
        logger.info(
            "Scheduling bad practice detection for pull request: {} at time {}",
            pullRequest.getId(),
            scheduledTime
        );
        BadPracticeDetectorTask badPracticeDetectorTask = createBadPracticeDetectorTask(
            pullRequest,
            sendBadPracticeDetectionEmail
        );

        if (scheduledTasks.containsKey(pullRequest.getId())) {
            List<ScheduledFuture<?>> scheduledTasksList = scheduledTasks.get(pullRequest.getId());
            List<ScheduledFuture<?>> tasksToRemove = new ArrayList<>();
            scheduledTasksList.forEach(task -> {
                if (!task.isDone() && !task.isCancelled()) {
                    logger.info("Cancelling previous task for pull request: {}", pullRequest.getId());
                    task.cancel(false);
                } else {
                    tasksToRemove.add(task);
                }
            });
            scheduledTasksList.removeAll(tasksToRemove);
        } else {
            scheduledTasks.put(pullRequest.getId(), new ArrayList<>());
        }

        ScheduledFuture<?> scheduledTask = taskScheduler.schedule(badPracticeDetectorTask, scheduledTime);
        scheduledTasks.get(pullRequest.getId()).add(scheduledTask);
    }

    private BadPracticeDetectorTask createBadPracticeDetectorTask(
        PullRequest pullRequest,
        boolean sendBadPracticeDetectionEmail
    ) {
        BadPracticeDetectorTask badPracticeDetectorTask = new BadPracticeDetectorTask();
        badPracticeDetectorTask.setPullRequestBadPracticeDetector(pullRequestBadPracticeDetector);
        badPracticeDetectorTask.setMailService(mailService);
        badPracticeDetectorTask.setPullRequest(pullRequest);
        badPracticeDetectorTask.setSendBadPracticeDetectionEmail(sendBadPracticeDetectionEmail);
        return badPracticeDetectorTask;
    }

    @Scheduled(cron = "0 0 */12 * * *")
    public void checkScheduledTasks() {
        logger.info("Running scheduled tasks check to remove completed tasks");
        List<Long> toRemovePullrequestIds = new ArrayList<>();
        scheduledTasks.forEach((pullRequestId, scheduledTasksList) -> {
            scheduledTasksList.removeIf(task -> task.isDone() || task.isCancelled());
            if (scheduledTasksList.isEmpty()) {
                toRemovePullrequestIds.add(pullRequestId);
            }
        });

        toRemovePullrequestIds.forEach(scheduledTasks::remove);
    }
}
