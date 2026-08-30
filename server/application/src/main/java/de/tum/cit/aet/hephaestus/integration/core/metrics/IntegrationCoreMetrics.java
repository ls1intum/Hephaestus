package de.tum.cit.aet.hephaestus.integration.core.metrics;

public final class IntegrationCoreMetrics {

    public static final String INTEGRATION_SYNC_PUSH_MESSAGES = "integration.sync.push.messages";
    public static final String INTEGRATION_SYNC_SSE_SUBSCRIBERS = "integration.sync.sse.subscribers";
    public static final String OAUTH_STATE_NONCE_PRUNED = "oauth.state.nonce.pruned";
    public static final String WEBHOOK_PUBLISH = "webhook.publish";
    public static final String WEBHOOK_PUBLISH_RETRY = "webhook.publish.retry";
    public static final String WEBHOOK_REJECTED = "webhook.rejected";
    public static final String WEBHOOK_STREAM_OLDEST_MESSAGE_AGE = "webhook.stream.oldest.message.age";
    public static final String WEBHOOK_STREAM_POLL_AGE = "webhook.stream.poll.age";
    public static final String WEBHOOK_STREAM_UNACKNOWLEDGED_DELETIONS = "webhook.stream.unacknowledged.deletions";
    public static final String WEBHOOK_STREAM_UNACKNOWLEDGED_GAP = "webhook.stream.unacknowledged.gap";

    private IntegrationCoreMetrics() {}
}
