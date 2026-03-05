package de.tum.in.www1.hephaestus.workspace.dto;

import org.springframework.lang.Nullable;

/**
 * Result of a GitLab webhook auto-registration attempt.
 *
 * <p>Used internally by {@link de.tum.in.www1.hephaestus.workspace.GitLabWebhookService}
 * to communicate the outcome to the activation service. Not exposed via REST API.
 *
 * @param registered    {@code true} if the webhook was successfully registered or already existed
 * @param webhookId     the GitLab webhook ID (non-null when registered)
 * @param groupId       the GitLab group numeric ID (non-null when registered)
 * @param failureReason human-readable reason when registration failed or was skipped
 */
public record WebhookSetupResult(
    boolean registered,
    @Nullable Long webhookId,
    @Nullable Long groupId,
    @Nullable String failureReason
) {
    public static WebhookSetupResult success(long webhookId, long groupId) {
        return new WebhookSetupResult(true, webhookId, groupId, null);
    }

    public static WebhookSetupResult failed(String reason) {
        return new WebhookSetupResult(false, null, null, reason);
    }

    public static WebhookSetupResult skipped(String reason) {
        return new WebhookSetupResult(false, null, null, reason);
    }
}
