package de.tum.in.www1.hephaestus.workspace;

import org.springframework.lang.Nullable;

/**
 * Result of a GitLab webhook auto-registration attempt.
 *
 * <p>Used internally by {@link GitLabWebhookService} to communicate the outcome
 * to the activation service. Not exposed via REST API.
 *
 * @param status        the outcome status (SUCCESS, SKIPPED, or FAILED)
 * @param webhookId     the GitLab webhook ID (non-null when successful)
 * @param groupId       the GitLab group numeric ID (non-null when successful)
 * @param failureReason human-readable reason when registration failed or was skipped
 */
public record WebhookSetupResult(
    Status status,
    @Nullable Long webhookId,
    @Nullable Long groupId,
    @Nullable String failureReason
) {
    public enum Status {
        SUCCESS,
        SKIPPED,
        FAILED,
    }

    /** Convenience: returns {@code true} when the webhook was successfully registered or already existed. */
    public boolean registered() {
        return status == Status.SUCCESS;
    }

    public static WebhookSetupResult success(long webhookId, long groupId) {
        return new WebhookSetupResult(Status.SUCCESS, webhookId, groupId, null);
    }

    public static WebhookSetupResult failed(String reason) {
        return new WebhookSetupResult(Status.FAILED, null, null, reason);
    }

    public static WebhookSetupResult skipped(String reason) {
        return new WebhookSetupResult(Status.SKIPPED, null, null, reason);
    }
}
