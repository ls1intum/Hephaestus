package de.tum.in.www1.hephaestus.practices.detection;

import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.practices.spi.BadPracticeNotificationSender;
import de.tum.in.www1.hephaestus.practices.spi.UserRoleChecker;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for bad practice detection tasks.
 * Schedules detection based on PR events (opened, labeled, closed) and user roles.
 */
@Component
public class BadPracticeDetectorScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BadPracticeDetectorScheduler.class);

    private static final String READY_TO_REVIEW = "ready to review";
    private static final String READY_FOR_REVIEW = "ready for review";
    private static final String READY_TO_MERGE = "ready to merge";

    private final TaskScheduler taskScheduler;
    private final PullRequestBadPracticeDetector pullRequestBadPracticeDetector;
    private final BadPracticeNotificationSender notificationSender;
    private final UserRoleChecker userRoleChecker;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final WorkspaceRepository workspaceRepository;
    private final boolean runAutomaticDetectionForAll;

    private final Map<Long, List<ScheduledFuture<?>>> scheduledTasks = new ConcurrentHashMap<>();

    public BadPracticeDetectorScheduler(
        TaskScheduler taskScheduler,
        PullRequestBadPracticeDetector pullRequestBadPracticeDetector,
        BadPracticeNotificationSender notificationSender,
        UserRoleChecker userRoleChecker,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        WorkspaceRepository workspaceRepository,
        @Value("${hephaestus.detection.run-automatic-detection-for-all}") boolean runAutomaticDetectionForAll
    ) {
        this.taskScheduler = taskScheduler;
        this.pullRequestBadPracticeDetector = pullRequestBadPracticeDetector;
        this.notificationSender = notificationSender;
        this.userRoleChecker = userRoleChecker;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.workspaceRepository = workspaceRepository;
        this.runAutomaticDetectionForAll = runAutomaticDetectionForAll;
    }

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

    /**
     * Triggers immediate bad practice detection when a ready-related label is added.
     * Used by the event-based architecture where individual label events are received.
     *
     * @param pullRequest the PR that was labeled
     * @param labelName   the name of the label that was added
     */
    public void detectBadPracticeForPrIfReadyLabel(PullRequest pullRequest, String labelName) {
        if (
            READY_TO_REVIEW.equals(labelName) || READY_FOR_REVIEW.equals(labelName) || READY_TO_MERGE.equals(labelName)
        ) {
            runAutomaticDetectionForAllIfEnabled(pullRequest, Instant.now(), true);
        }
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

        if (!userRoleChecker.isHealthy()) {
            logger.debug(
                "Skipping role check for PR {} because the role checker is marked unhealthy",
                pullRequest.getId()
            );
            return;
        }

        if (userRoleChecker.hasAutomaticDetectionRole(assignee.getLogin())) {
            logger.info("User {} has the run_automatic_detection role. Scheduling detection.", assignee.getLogin());
            scheduleDetectionAtTime(pullRequest, scheduledTime, sendBadPracticeDetectionEmail);
        } else {
            logger.info(
                "User {} does not have the run_automatic_detection role. Skipping detection.",
                assignee.getLogin()
            );
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
            scheduledTasks.put(pullRequest.getId(), new CopyOnWriteArrayList<>());
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
        badPracticeDetectorTask.setNotificationSender(notificationSender);
        badPracticeDetectorTask.setPullRequest(pullRequest);
        badPracticeDetectorTask.setSendBadPracticeDetectionEmail(sendBadPracticeDetectionEmail);
        resolveWorkspaceSlugForRepository(pullRequest.getRepository()).ifPresent(
            badPracticeDetectorTask::setWorkspaceSlug
        );
        return badPracticeDetectorTask;
    }

    /**
     * Resolve workspace slug for a repository without depending on WorkspaceService to avoid circular dependencies.
     * Prefers repository monitor mapping; falls back to account login match.
     */
    private Optional<String> resolveWorkspaceSlugForRepository(Repository repository) {
        if (repository == null || repository.getNameWithOwner() == null) {
            return Optional.empty();
        }
        String nameWithOwner = repository.getNameWithOwner();
        var monitor = repositoryToMonitorRepository.findByNameWithOwner(nameWithOwner);
        if (monitor.isPresent()) {
            Workspace workspace = monitor.get().getWorkspace();
            return workspace != null ? Optional.ofNullable(workspace.getWorkspaceSlug()) : Optional.empty();
        }
        String owner = nameWithOwner.contains("/") ? nameWithOwner.substring(0, nameWithOwner.indexOf("/")) : null;
        if (owner != null) {
            return workspaceRepository.findByAccountLoginIgnoreCase(owner).map(Workspace::getWorkspaceSlug);
        }
        return Optional.empty();
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
