package de.tum.cit.aet.hephaestus.integration.handler;

import de.tum.cit.aet.hephaestus.integration.spi.EventTypeKey;
import io.nats.client.Message;

/**
 * Unified, vendor-neutral message-handler contract.
 *
 * <p>This is the SPI side of the {@code IntegrationMessageHandlerRegistry} — one handler
 * per {@link EventTypeKey}. Concrete implementations live under
 * {@code integration/<kind>/...} (e.g. {@code integration/github/handler/PullRequestHandler}).
 * During the transition window opened by plan v4 D7/D22 the legacy
 * {@code gitprovider/common/github/GitHubMessageHandler} and
 * {@code gitprovider/common/gitlab/GitLabMessageHandler} hierarchies continue to operate
 * via their existing per-kind registries; this surface is the migration target. Once a
 * handler moves over, it deregisters from the legacy registry and gets picked up here.
 *
 * <p>The contract intentionally stays minimal:
 * <ul>
 *   <li>{@link #key()} — the registry index; one key maps to at most one handler.</li>
 *   <li>{@link #onMessage(Message)} — the NATS entry point. Implementations own their
 *       own deserialization and transaction boundaries (so the framework stays
 *       I/O-free and registry lookups remain pure functions).</li>
 * </ul>
 *
 * <p>Reusable helpers (TransactionTemplate wrapping, Jackson deserialization,
 * subject-suffix validation) can later be added as defaulted utilities on this
 * interface or via an abstract base. They are deliberately omitted now: the goal of
 * #1198 is to land the routing surface without coupling it to either the legacy
 * deserializer or the transaction strategy. Both will be revisited when the first
 * concrete handler migrates.
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
