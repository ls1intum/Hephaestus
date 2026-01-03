package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.github;

import org.springframework.stereotype.Service;

/**
 * Service for syncing pull request review threads.
 * <p>
 * All thread state operations are delegated to {@link GitHubPullRequestReviewThreadProcessor}.
 * This service is retained for batch sync operations if needed.
 */
@Service
public class GitHubPullRequestReviewThreadSyncService {

    private final GitHubPullRequestReviewThreadProcessor threadProcessor;

    public GitHubPullRequestReviewThreadSyncService(GitHubPullRequestReviewThreadProcessor threadProcessor) {
        this.threadProcessor = threadProcessor;
    }

    /**
     * Resolves a thread by ID.
     *
     * @param threadId the thread ID
     */
    public void resolveThread(Long threadId) {
        threadProcessor.resolve(threadId);
    }

    /**
     * Unresolves a thread by ID.
     *
     * @param threadId the thread ID
     */
    public void unresolveThread(Long threadId) {
        threadProcessor.unresolve(threadId);
    }
}
