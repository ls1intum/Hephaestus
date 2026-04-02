package de.tum.in.www1.hephaestus.agent.job;

import static de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent.TriggerEventNames;

import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.handler.PullRequestReviewSubmissionRequest;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.practices.review.GateDecision;
import de.tum.in.www1.hephaestus.practices.review.PracticeReviewDetectionGate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes bot commands from MR comments (e.g., {@code /hephaestus review}).
 *
 * <p>Triggered by {@link de.tum.in.www1.hephaestus.gitprovider.issuecomment.gitlab.GitLabNoteMessageHandler}
 * when a non-system MR comment matches a known command pattern. Runs asynchronously
 * to avoid blocking webhook processing.
 *
 * <p>The command prefix is {@code /hephaestus} (case-insensitive). Supported commands:
 * <ul>
 *   <li>{@code /hephaestus review} — retrigger a practice review on the MR</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.agent.nats", name = "enabled", havingValue = "true")
public class BotCommandProcessor {

    private static final Logger log = LoggerFactory.getLogger(BotCommandProcessor.class);

    /** Prefix for bot commands (case-insensitive match). */
    private static final String COMMAND_PREFIX = "/hephaestus ";

    private final AgentJobService agentJobService;
    private final PullRequestRepository pullRequestRepository;
    private final PracticeReviewDetectionGate practiceReviewDetectionGate;

    public BotCommandProcessor(
        AgentJobService agentJobService,
        PullRequestRepository pullRequestRepository,
        PracticeReviewDetectionGate practiceReviewDetectionGate
    ) {
        this.agentJobService = agentJobService;
        this.pullRequestRepository = pullRequestRepository;
        this.practiceReviewDetectionGate = practiceReviewDetectionGate;
    }

    /**
     * Check if a comment body contains a bot command.
     *
     * @param noteBody the raw comment body
     * @return true if the body starts with the command prefix (case-insensitive)
     */
    public static boolean isBotCommand(String noteBody) {
        if (noteBody == null || noteBody.isBlank()) {
            return false;
        }
        return noteBody.strip().toLowerCase().startsWith(COMMAND_PREFIX);
    }

    /**
     * Process a bot command from an MR comment. Runs in a new transaction asynchronously.
     *
     * @param repositoryId the repository's DB id
     * @param mrNumber     the merge request iid (project-scoped number)
     * @param noteBody     the raw comment body (e.g., "/hephaestus review")
     * @param noteAuthor   the login of the note author (for logging)
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processCommand(long repositoryId, int mrNumber, String noteBody, String noteAuthor) {
        String command = noteBody.strip().toLowerCase();

        if (command.startsWith(COMMAND_PREFIX + "review")) {
            handleReviewCommand(repositoryId, mrNumber, noteAuthor);
        } else {
            log.debug(
                "Unknown bot command: command={}, repoId={}, mrNumber={}, author={}",
                command,
                repositoryId,
                mrNumber,
                noteAuthor
            );
        }
    }

    private void handleReviewCommand(long repositoryId, int mrNumber, String noteAuthor) {
        try {
            // 1. Find the PR entity, then re-fetch with full associations for the gate
            PullRequest stub = pullRequestRepository.findByRepositoryIdAndNumber(repositoryId, mrNumber).orElse(null);
            if (stub == null) {
                log.warn(
                    "Bot command: PR not found, repoId={}, mrNumber={}, author={}",
                    repositoryId,
                    mrNumber,
                    noteAuthor
                );
                return;
            }

            PullRequest pr = pullRequestRepository.findByIdWithAllForGate(stub.getId()).orElse(null);
            if (pr == null) {
                log.warn("Bot command: PR disappeared during re-fetch, prId={}", stub.getId());
                return;
            }

            // 2. Validate branch info
            if (pr.getHeadRefOid() == null || pr.getHeadRefName() == null || pr.getBaseRefName() == null) {
                log.warn(
                    "Bot command: missing branch info, prId={}, headRefOid={}, headRefName={}, baseRefName={}",
                    pr.getId(),
                    pr.getHeadRefOid(),
                    pr.getHeadRefName(),
                    pr.getBaseRefName()
                );
                return;
            }

            // 3. Skip closed/merged PRs
            if (
                pr.getState() == PullRequest.State.CLOSED || pr.getState() == PullRequest.State.MERGED || pr.isMerged()
            ) {
                log.info("Bot command: skipping closed/merged PR, prId={}, state={}", pr.getId(), pr.getState());
                return;
            }

            // 4. Evaluate practice detection gate (uses PullRequestCreated to match broadest set)
            GateDecision decision = practiceReviewDetectionGate.evaluate(pr, TriggerEventNames.PULL_REQUEST_CREATED);

            switch (decision) {
                case GateDecision.Skip skip -> log.info(
                    "Bot command: review skipped by gate, prId={}, mrNumber={}, reason={}, author={}",
                    pr.getId(),
                    mrNumber,
                    skip.reason(),
                    noteAuthor
                );
                case GateDecision.Detect detect -> {
                    EventPayload.PullRequestData prData = EventPayload.PullRequestData.from(pr);
                    PullRequestReviewSubmissionRequest request = new PullRequestReviewSubmissionRequest(
                        prData,
                        pr.getHeadRefName(),
                        pr.getHeadRefOid(),
                        pr.getBaseRefName()
                    );

                    agentJobService
                        .submit(detect.workspace().getId(), AgentJobType.PULL_REQUEST_REVIEW, request)
                        .ifPresentOrElse(
                            job ->
                                log.info(
                                    "Bot command: review triggered, jobId={}, prId={}, mrNumber={}, author={}, matchedPractices={}",
                                    job.getId(),
                                    pr.getId(),
                                    mrNumber,
                                    noteAuthor,
                                    detect.matchedPractices().size()
                                ),
                            () ->
                                log.warn(
                                    "Bot command: no job created (no enabled agent config?), prId={}, mrNumber={}",
                                    pr.getId(),
                                    mrNumber
                                )
                        );
                }
            }
        } catch (Exception e) {
            log.error(
                "Bot command: failed to process review, repoId={}, mrNumber={}, author={}",
                repositoryId,
                mrNumber,
                noteAuthor,
                e
            );
        }
    }
}
