package de.tum.in.www1.hephaestus.gitprovider.common.github;

import java.io.IOException;
import java.util.Locale;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes GitHub API calls with automatic retry/invalidation when authentication errors occur.
 */
@Component
public class GitHubClientExecutor {

    private static final Logger logger = LoggerFactory.getLogger(GitHubClientExecutor.class);

    private final GitHubClientProvider gitHubClientProvider;

    public GitHubClientExecutor(GitHubClientProvider gitHubClientProvider) {
        this.gitHubClientProvider = gitHubClientProvider;
    }

    public <T> T execute(Long workspaceId, GitHubCallback<T> callback) throws IOException {
        IOException lastIOException = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            GitHub client = gitHubClientProvider.forWorkspace(workspaceId);
            try {
                return callback.doWith(client);
            } catch (HttpException httpException) {
                if (isAuthenticationFailure(httpException)) {
                    logger.warn(
                        "GitHub API authentication failure for workspace {} (HTTP {}): {}",
                        workspaceId,
                        httpException.getResponseCode(),
                        httpException.getMessage()
                    );
                    gitHubClientProvider.invalidateWorkspace(workspaceId);
                    if (attempt == 0) {
                        continue;
                    }
                }
                throw httpException;
            } catch (IOException ioException) {
                lastIOException = ioException;
                break;
            }
        }
        if (lastIOException != null) {
            throw lastIOException;
        }
        return null;
    }

    private boolean isAuthenticationFailure(HttpException exception) {
        int code = exception.getResponseCode();
        return code == 401 || code == 403 || (code == 422 && containsSuspensionMessage(exception.getMessage()));
    }

    private boolean containsSuspensionMessage(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ENGLISH);
        return normalized.contains("suspended") || normalized.contains("installation has been suspended");
    }

    @FunctionalInterface
    public interface GitHubCallback<T> {
        T doWith(GitHub client) throws IOException;
    }
}
