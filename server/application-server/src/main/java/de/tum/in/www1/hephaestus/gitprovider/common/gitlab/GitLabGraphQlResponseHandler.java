package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabExceptionClassifier.Category;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabExceptionClassifier.ClassificationResult;
import org.slf4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Handles GraphQL response validation, error classification, and rate-limit
 * back-off for GitLab sync services.  Drop-in replacement for the repeated
 * {@code if (response == null || !response.isValid())} blocks that previously
 * treated every failure as an opaque error.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabGraphQlResponseHandler {

    private static final long MAX_RATE_LIMIT_WAIT_MS = 120_000;

    private final GitLabExceptionClassifier exceptionClassifier;

    public GitLabGraphQlResponseHandler(GitLabExceptionClassifier exceptionClassifier) {
        this.exceptionClassifier = exceptionClassifier;
    }

    /**
     * Result of handling a GraphQL response.
     *
     * @param action what the caller should do next
     * @param classification classified error details (null when CONTINUE)
     */
    public record HandleResult(Action action, @Nullable ClassificationResult classification) {
        public enum Action {
            /** Response is valid — proceed with data processing. */
            CONTINUE,
            /** Rate-limited — back-off was performed, caller should retry the same page. */
            RETRY,
            /** Unrecoverable error — caller should abort the pagination loop. */
            ABORT,
        }
    }

    /**
     * Validates a GraphQL response and classifies errors.
     * <p>
     * If the response is rate-limited, this method sleeps for the suggested duration
     * (up to 120 s) and returns {@link HandleResult.Action#RETRY}.
     * For all other errors it returns {@link HandleResult.Action#ABORT}.
     *
     * @param response the raw GraphQL response (may be null)
     * @param context human-readable description for log messages (e.g. "issues for project X")
     * @param log the caller's logger
     * @return action to take, or {@code CONTINUE} if response is valid
     */
    public HandleResult handle(@Nullable ClientGraphQlResponse response, String context, Logger log) {
        if (response != null && response.isValid()) {
            // Check for partial errors even on valid responses
            if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                var classification = exceptionClassifier.classifyGraphQlResponse(response);
                if (classification != null) {
                    log.warn(
                        "Partial GraphQL errors (data still valid): context={}, category={}, message={}",
                        context,
                        classification.category(),
                        classification.message()
                    );
                }
            }
            return new HandleResult(HandleResult.Action.CONTINUE, null);
        }

        // Response is null or invalid — classify the error
        var classification = response != null ? exceptionClassifier.classifyGraphQlResponse(response) : null;

        if (classification != null) {
            log.warn(
                "GraphQL error classified: context={}, category={}, message={}",
                context,
                classification.category(),
                classification.message()
            );

            if (classification.category() == Category.RATE_LIMITED && classification.suggestedWait() != null) {
                long waitMs = Math.min(classification.suggestedWait().toMillis(), MAX_RATE_LIMIT_WAIT_MS);
                log.info("Rate-limited, waiting {}ms before retry: context={}", waitMs, context);
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new HandleResult(HandleResult.Action.ABORT, classification);
                }
                return new HandleResult(HandleResult.Action.RETRY, classification);
            }

            // CLIENT_ERROR (complexity), AUTH_ERROR, NOT_FOUND, UNKNOWN -> abort
            return new HandleResult(HandleResult.Action.ABORT, classification);
        }

        // No classification possible — generic failure
        log.warn(
            "GraphQL request failed (unclassified): context={}, errors={}",
            context,
            response != null ? response.getErrors() : "null response"
        );
        return new HandleResult(HandleResult.Action.ABORT, null);
    }

    /**
     * Checks whether a pagination cursor is stuck in a loop (same value as previous page).
     *
     * @param currentCursor  the cursor returned by the current page
     * @param previousCursor the cursor from the previous page
     * @param context        human-readable description for log messages
     * @param log            the caller's logger
     * @return true if a loop is detected (caller should break)
     */
    public boolean isPaginationLoop(
        @Nullable String currentCursor,
        @Nullable String previousCursor,
        String context,
        Logger log
    ) {
        if (currentCursor != null && currentCursor.equals(previousCursor)) {
            log.error(
                "Pagination loop detected (same cursor returned twice): context={}, cursor={}",
                context,
                currentCursor
            );
            return true;
        }
        return false;
    }
}
