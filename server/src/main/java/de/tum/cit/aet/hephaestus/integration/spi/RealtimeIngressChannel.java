package de.tum.cit.aet.hephaestus.integration.spi;

/**
 * SPI for WebSocket-first vendors (Discord Gateway, Slack Socket Mode, Microsoft
 * Bot Framework Direct Line). Peer of the HTTP-webhook trait — different ingress
 * semantics, same downstream pipeline.
 *
 * <p>Scaffolded in #1198. No implementations ship until a vendor that needs it
 * lands (#1204 Slack Socket Mode or a Discord epic). Gating the trait module on
 * a dedicated runtime role keeps it out of the webhook / app-server boot paths.
 */
public interface RealtimeIngressChannel {

    IntegrationKind kind();

    /**
     * Open the realtime session for the given Connection. Implementations run on a
     * dedicated virtual thread per session and emit incoming events as
     * {@code SyncMessage.Record}s into the same downstream pipeline used by webhook
     * ingest. Returning signals graceful shutdown; throwing signals fatal error.
     */
    void run(IntegrationRef ref, RealtimeContext context) throws InterruptedException;

    interface RealtimeContext {
        /** Returns false when the orchestrator wants the session to exit. */
        boolean shouldContinue();

        /** Surfaces an incoming event to the downstream pipeline. */
        void publish(byte[] body, java.util.Map<String, String> headers);

        /** Records a heartbeat / no-op so health checks see liveness. */
        void heartbeat();
    }
}
