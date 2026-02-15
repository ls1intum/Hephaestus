package de.tum.in.www1.hephaestus.gitprovider.common.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.Category;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.ClassificationResult;
import org.slf4j.Logger;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class GitHubGraphQlSyncHelper {

    private static final long MAX_RATE_LIMIT_WAIT_MS = 300_000;

    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubExceptionClassifier exceptionClassifier;

    public GitHubGraphQlSyncHelper(
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubExceptionClassifier exceptionClassifier
    ) {
        this.graphQlClientProvider = graphQlClientProvider;
        this.exceptionClassifier = exceptionClassifier;
    }

    @Nullable
    public ClassificationResult classifyGraphQlErrors(@Nullable ClientGraphQlResponse response) {
        ClassificationResult classification = exceptionClassifier.classifyGraphQlResponse(response);
        if (classification != null) {
            return classification;
        }

        GitHubGraphQlErrorUtils.TransientError transientError = GitHubGraphQlErrorUtils.detectTransientError(response);
        if (transientError == null) {
            return null;
        }

        return switch (transientError.type()) {
            case RATE_LIMIT -> ClassificationResult.rateLimited(
                transientError.getRecommendedWait(),
                "GraphQL rate limit: " + transientError.message()
            );
            case TIMEOUT, SERVER_ERROR -> ClassificationResult.of(
                Category.RETRYABLE,
                "GraphQL transient error: " + transientError.message()
            );
            case RESOURCE_LIMIT -> ClassificationResult.of(
                Category.CLIENT_ERROR,
                "GraphQL resource limit: " + transientError.message()
            );
        };
    }

    public boolean handleGraphQlClassification(
        ClassificationResult classification,
        int retryAttempt,
        int maxRetryAttempts,
        String phase,
        String scopeLabel,
        Object scopeValue,
        Logger log
    ) {
        Category category = classification.category();

        switch (category) {
            case RETRYABLE -> {
                if (retryAttempt < maxRetryAttempts) {
                    log.warn(
                        "Retrying {} after transient GraphQL error: {}={}, attempt={}, error={}",
                        phase,
                        scopeLabel,
                        scopeValue,
                        retryAttempt + 1,
                        classification.message()
                    );
                    try {
                        ExponentialBackoff.sleep(retryAttempt + 1);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    return true;
                }
                log.error(
                    "Failed {} after {} retries due to GraphQL error: {}={}, error={}",
                    phase,
                    maxRetryAttempts,
                    scopeLabel,
                    scopeValue,
                    classification.message()
                );
                return false;
            }
            case RATE_LIMITED -> {
                if (retryAttempt < maxRetryAttempts && classification.suggestedWait() != null) {
                    long waitMs = Math.min(classification.suggestedWait().toMillis(), MAX_RATE_LIMIT_WAIT_MS);
                    log.warn(
                        "Rate limited during {}, waiting: {}={}, waitMs={}",
                        phase,
                        scopeLabel,
                        scopeValue,
                        waitMs
                    );
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    return true;
                }
                log.error(
                    "Aborting {} due to GraphQL rate limit: {}={}, error={}",
                    phase,
                    scopeLabel,
                    scopeValue,
                    classification.message()
                );
                return false;
            }
            case NOT_FOUND -> {
                log.warn(
                    "Resource not found during {} GraphQL response: {}={}, error={}",
                    phase,
                    scopeLabel,
                    scopeValue,
                    classification.message()
                );
                return false;
            }
            case AUTH_ERROR -> {
                log.error(
                    "Authentication error during {} GraphQL response: {}={}, error={}",
                    phase,
                    scopeLabel,
                    scopeValue,
                    classification.message()
                );
                return false;
            }
            case CLIENT_ERROR -> {
                log.error(
                    "Client error during {} GraphQL response: {}={}, error={}",
                    phase,
                    scopeLabel,
                    scopeValue,
                    classification.message()
                );
                return false;
            }
            default -> {
                log.error(
                    "Aborting {} due to GraphQL error: {}={}, category={}, error={}",
                    phase,
                    scopeLabel,
                    scopeValue,
                    category,
                    classification.message()
                );
                return false;
            }
        }
    }

    public boolean waitForRateLimitIfNeeded(
        Long scopeId,
        String phase,
        String scopeLabel,
        Object scopeValue,
        Logger log
    ) {
        try {
            boolean waited = graphQlClientProvider.waitIfRateLimitLow(scopeId);
            if (waited) {
                log.info("Paused due to critical rate limit: phase={}, {}={}", phase, scopeLabel, scopeValue);
            }
            // Both waited=true ("waited, now continue") and waited=false
            // ("not critical / reset passed, continue") mean the sync should proceed.
            // The tracker optimistically resets remaining when the reset time has
            // passed, so isRateLimitCritical() will return false on the next check.
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for rate limit reset: phase={}, {}={}", phase, scopeLabel, scopeValue);
            return false;
        }
    }
}
