package de.tum.cit.aet.hephaestus.integration.consumer;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Shared read/write holder for the live consumer fleet's status.
 *
 * <p>Introduced in plan v4 D9 as the seam between the soon-to-be-deleted
 * {@code NatsConsumerService} (the orchestrator that writes the state) and the new
 * {@link IntegrationConsumerHealthIndicator} (the actuator probe that reads it).
 *
 * <p><b>Design choice.</b> A concrete {@link Component} rather than an interface +
 * implementation because:
 * <ul>
 *   <li>There is exactly ONE consumer fleet in this process (the C13 follow-up that adds
 *       a second is the moment to introduce a per-fleet abstraction). Premature interface
 *       extraction would just spread setup across two files.</li>
 *   <li>Keeping the writer surface separate from {@code NatsConsumerService} prevents the
 *       writer methods from inflating the orchestrator's business-method count past the
 *       "split this service" architecture rule.</li>
 *   <li>Updating fields through a held collaborator (rather than implementing an
 *       interface on the service) lets the health indicator read while the orchestrator
 *       is mid-restart, without any locking.</li>
 * </ul>
 *
 * <p>All writers are allocation-free on the hot path: timestamps update an
 * {@link AtomicReference}, counts update an {@link AtomicInteger}. All readers are
 * lock-free and may be called from the actuator probe thread at any point.
 */
@Component
public class IntegrationConsumerStats {

    private static final String STATUS_UNINITIALISED = null;

    private final AtomicReference<String> natsStatus = new AtomicReference<>(STATUS_UNINITIALISED);
    private final AtomicInteger activeScopeConsumers = new AtomicInteger(0);
    private final AtomicReference<Boolean> installationActive = new AtomicReference<>(Boolean.FALSE);
    private final AtomicReference<Instant> lastDispatchAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastNakAt = new AtomicReference<>();

    // -------------------------------------------------------------------------
    // Readers (called from the health probe; never blocking)
    // -------------------------------------------------------------------------

    /**
     * @return the underlying NATS connection status as a coarse-grained label
     *     (typically {@code CONNECTED}, {@code DISCONNECTED}, {@code CLOSED}, …).
     *     {@link Optional#empty()} when no connection has been attempted yet.
     */
    public Optional<String> natsConnectionStatus() {
        return Optional.ofNullable(natsStatus.get());
    }

    /**
     * @return the number of currently-active scope consumers. Does not include the
     *     installation-wide consumer; see {@link #installationConsumerActive()}.
     */
    public int activeScopeConsumerCount() {
        return activeScopeConsumers.get();
    }

    /**
     * @return {@code true} when the installation-wide consumer has been started AND not
     *     yet stopped.
     */
    public boolean installationConsumerActive() {
        return Boolean.TRUE.equals(installationActive.get());
    }

    /**
     * @return the timestamp of the most recent successful message dispatch, if any. The
     *     "no message yet" case returns {@link Optional#empty()} so the probe can render
     *     a dash instead of a confusing epoch zero.
     */
    public Optional<Instant> lastDispatchAt() {
        return Optional.ofNullable(lastDispatchAt.get());
    }

    /**
     * @return the timestamp of the most recent NAK (poison or transient), if any.
     */
    public Optional<Instant> lastNakAt() {
        return Optional.ofNullable(lastNakAt.get());
    }

    // -------------------------------------------------------------------------
    // Writers (called by the consumer fleet's lifecycle + message loop)
    // -------------------------------------------------------------------------

    /** Update the cached connection-status label. Null tolerated (treated as uninitialised). */
    public void setNatsConnectionStatus(@Nullable String status) {
        natsStatus.set(status);
    }

    /** Replace the active scope consumer count. */
    public void setActiveScopeConsumerCount(int count) {
        activeScopeConsumers.set(Math.max(0, count));
    }

    /** Set whether the installation-wide consumer is currently running. */
    public void setInstallationConsumerActive(boolean active) {
        installationActive.set(active);
    }

    /** Record a successful dispatch (handler returned without throwing). Null tolerated. */
    public void recordDispatch(@Nullable Instant at) {
        if (at != null) {
            lastDispatchAt.set(at);
        }
    }

    /** Record a NAK (poison or transient). Null tolerated. */
    public void recordNak(@Nullable Instant at) {
        if (at != null) {
            lastNakAt.set(at);
        }
    }
}
