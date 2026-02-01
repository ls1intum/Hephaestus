package de.tum.in.www1.hephaestus.practices.detection;

import static de.tum.in.www1.hephaestus.practices.model.PullRequestLabels.READY_FOR_REVIEW;
import static de.tum.in.www1.hephaestus.practices.model.PullRequestLabels.READY_TO_MERGE;
import static de.tum.in.www1.hephaestus.practices.model.PullRequestLabels.READY_TO_REVIEW;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.practices.DetectionProperties;
import de.tum.in.www1.hephaestus.practices.spi.BadPracticeNotificationSender;
import de.tum.in.www1.hephaestus.practices.spi.UserRoleChecker;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for bad practice detection tasks.
 * Schedules detection based on PR events (opened, labeled, closed) and user roles.
 */
@Component
public class BadPracticeDetectorScheduler {

    private static final Logger log = LoggerFactory.getLogger(BadPracticeDetectorScheduler.class);
    private static final Duration SKIP_WARNING_INTERVAL = Duration.ofSeconds(30);

    private final TaskScheduler taskScheduler;
    private final PullRequestBadPracticeDetector pullRequestBadPracticeDetector;
    private final BadPracticeNotificationSender notificationSender;
    private final UserRoleChecker userRoleChecker;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final WorkspaceRepository workspaceRepository;
    private final boolean runAutomaticDetectionForAll;

    private final Map<Long, List<ScheduledFuture<?>>> scheduledTasks = new ConcurrentHashMap<>();

    private final AtomicLong skippedDueToKeycloakCount = new AtomicLong(0);
    private final AtomicReference<Instant> lastSkipWarningTime = new AtomicReference<>(Instant.EPOCH);

    public BadPracticeDetectorScheduler(
        TaskScheduler taskScheduler,
        PullRequestBadPracticeDetector pullRequestBadPracticeDetector,
        BadPracticeNotificationSender notificationSender,
        UserRoleChecker userRoleChecker,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        WorkspaceRepository workspaceRepository,
        DetectionProperties detectionProperties
    ) {
        this.taskScheduler = taskScheduler;
        this.pullRequestBadPracticeDetector = pullRequestBadPracticeDetector;
        this.notificationSender = notificationSender;
        this.userRoleChecker = userRoleChecker;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.workspaceRepository = workspaceRepository;
        this.runAutomaticDetectionForAll = detectionProperties.runAutomaticDetectionForAll();
    }

    public void detectBadPracticeForPrWhenOpenedOrReadyForReviewEvent(PullRequest pullRequest) {
        Instant timeInOneHour = Instant.now().plusSeconds(3600);
        runAutomaticDetectionForAllIfEnabled(pullRequest, timeInOneHour, true);
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
            logSkippedDueToKeycloak(pullRequest, assignee);
            return;
        }

        // Keycloak is healthy - reset skip counter if it was previously incremented
        long previousCount = skippedDueToKeycloakCount.getAndSet(0);
        if (previousCount > 0) {
            log.info("Keycloak circuit breaker recovered, resuming bad practice detection");
        }

        if (userRoleChecker.hasAutomaticDetectionRole(assignee.getLogin())) {
            log.info("Scheduling detection: userLogin={}, reason=hasAutomaticDetectionRole", assignee.getLogin());
            scheduleDetectionAtTime(pullRequest, scheduledTime, sendBadPracticeDetectionEmail);
        } else {
            // DEBUG level: skipping is the expected default behavior when users haven't
            // logged in or don't have the automatic detection role assigned
            log.debug("Skipped detection: userLogin={}, reason=noAutomaticDetectionRole", assignee.getLogin());
        }
    }

    private void logSkippedDueToKeycloak(PullRequest pullRequest, User assignee) {
        long currentCount = skippedDueToKeycloakCount.incrementAndGet();
        Instant now = Instant.now();
        Instant lastWarning = lastSkipWarningTime.get();

        // Always log at DEBUG level for detailed troubleshooting
        log.debug(
            "Skipped bad practice detection due to Keycloak circuit breaker open: prId={}, prTitle={}, assignee={}",
            pullRequest.getId(),
            pullRequest.getTitle(),
            assignee.getLogin()
        );

        // Rate-limit WARN logging to avoid log spam during startup with many PRs
        if (Duration.between(lastWarning, now).compareTo(SKIP_WARNING_INTERVAL) >= 0) {
            if (lastSkipWarningTime.compareAndSet(lastWarning, now)) {
                log.warn(
                    "Skipped bad practice detection due to Keycloak circuit breaker open: skippedCount={}",
                    currentCount
                );
            }
        }
    }

    private void scheduleDetectionAtTime(
        PullRequest pullRequest,
        Instant scheduledTime,
        boolean sendBadPracticeDetectionEmail
    ) {
        log.info("Scheduling bad practice detection: prId={}, scheduledTime={}", pullRequest.getId(), scheduledTime);
        BadPracticeDetectorTask badPracticeDetectorTask = createBadPracticeDetectorTask(
            pullRequest,
            sendBadPracticeDetectionEmail
        );

        if (scheduledTasks.containsKey(pullRequest.getId())) {
            List<ScheduledFuture<?>> scheduledTasksList = scheduledTasks.get(pullRequest.getId());
            List<ScheduledFuture<?>> tasksToRemove = new ArrayList<>();
            scheduledTasksList.forEach(task -> {
                if (!task.isDone() && !task.isCancelled()) {
                    log.info("Cancelled previous detection task: prId={}", pullRequest.getId());
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
        log.info("Starting scheduled tasks cleanup");
        List<Long> toRemovePullrequestIds = new ArrayList<>();
        scheduledTasks.forEach((pullRequestId, scheduledTasksList) -> {
            scheduledTasksList.removeIf(task -> task.isDone() || task.isCancelled());
            if (scheduledTasksList.isEmpty()) {
                toRemovePullrequestIds.add(pullRequestId);
            }
        });

        toRemovePullrequestIds.forEach(scheduledTasks::remove);
    }

    /**
     * Cancels all scheduled bad practice detection tasks for the given pull request IDs.
     * Called during workspace purge to clean up scheduled tasks for PRs in the workspace.
     *
     * @param pullRequestIds the IDs of pull requests whose scheduled tasks should be cancelled
     * @return the number of tasks that were cancelled
     */
    public int cancelScheduledTasksForPullRequests(Collection<Long> pullRequestIds) {
        if (pullRequestIds == null || pullRequestIds.isEmpty()) {
            return 0;
        }

        int cancelledCount = 0;
        for (Long prId : pullRequestIds) {
            List<ScheduledFuture<?>> tasks = scheduledTasks.remove(prId);
            if (tasks != null) {
                for (ScheduledFuture<?> task : tasks) {
                    if (!task.isDone() && !task.isCancelled()) {
                        task.cancel(false);
                        cancelledCount++;
                    }
                }
            }
        }

        if (cancelledCount > 0) {
            log.info(
                "Cancelled scheduled bad practice detection tasks: cancelledCount={}, prCount={}",
                cancelledCount,
                pullRequestIds.size()
            );
        }

        return cancelledCount;
    }
}
