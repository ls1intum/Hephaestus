package de.tum.cit.aet.hephaestus.integration.core.handler;

import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import io.nats.client.Message;

/**
 * Unified, vendor-neutral message-handler contract.
 *
 * <p>This is the SPI side of the {@code IntegrationMessageHandlerRegistry} — one handler
 * per {@link EventTypeKey}. Concrete implementations live under
 * {@code integration/<kind>/...} and almost always extend
 * {@link AbstractIntegrationMessageHandler}, which folds in the reusable concerns
 * (subject-suffix validation, Jackson deserialization, transaction wrapping). The bare
 * interface is the contract; the abstract base is the convenience.
 *
 * <p>The contract intentionally stays minimal:
 * <ul>
 *   <li>{@link #key()} — the registry index; one key maps to at most one handler.</li>
 *   <li>{@link #onMessage(Message)} — the NATS entry point.</li>
 * </ul>
 *
 * <p><b>Threading.</b> {@link #onMessage(Message)} is invoked from the JetStream
 * consumer thread that delivered the message; implementations must be safe to call
 * concurrently for distinct keys but may assume serial delivery per
 * {@code (stream, subject)} as enforced by the JetStream consumer configuration.
 *
 * <p><b>Error handling.</b> Throwing from {@code onMessage} surfaces the error to the
 * consumer dispatcher, which decides between ACK, NACK, and dead-lettering. Handlers
 * should NOT swallow exceptions silently.
 */
public interface IntegrationMessageHandler {
    /**
     * @return the registry index for this handler; must be stable across the bean's
     *     lifetime and unique within the application context.
     */
    EventTypeKey key();

    /**
     * Process a single NATS message. The dispatcher guarantees the message's subject
     * was routed via the per-kind {@code SubjectParser} that produced this handler's
     * {@link #key()}, so implementations may skip re-validating the subject prefix.
     *
     * @param msg the NATS message; never null.
     */
    void onMessage(Message msg);
}
