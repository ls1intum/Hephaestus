package de.tum.cit.aet.hephaestus.gitprovider.webhook;

import de.tum.cit.aet.hephaestus.core.webhook.WebhookProperties;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.context.WebServerGracefulShutdownLifecycle;
import org.springframework.context.SmartLifecycle;

/**
 * Drains in-flight webhook publishes during graceful shutdown, AFTER the HTTP server has stopped
 * accepting new requests but BEFORE the NATS connection closes.
 *
 * <p>{@link SmartLifecycle} semantics: HIGHER phase stops FIRST. Sitting at a phase lower than
 * {@link WebServerGracefulShutdownLifecycle#SMART_LIFECYCLE_PHASE} means we stop after Spring's
 * web server — no new requests can arrive while we drain.
 *
 * <p>The drain budget comes from {@code hephaestus.webhook.shutdown.drain-timeout}. Docker's
 * {@code stop_grace_period} (40s in compose) must cover Spring's HTTP drain plus this budget.
 */
public class WebhookGracefulShutdown implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(WebhookGracefulShutdown.class);

    private static final int PHASE = WebServerGracefulShutdownLifecycle.SMART_LIFECYCLE_PHASE - 1024;

    private final JetStreamPublisher publisher;
    private final Duration drainTimeout;
    private final AtomicBoolean running = new AtomicBoolean(false);

    WebhookGracefulShutdown(JetStreamPublisher publisher, WebhookProperties properties) {
        this.publisher = publisher;
        this.drainTimeout = properties.shutdown().drainTimeout();
    }

    @Override
    public void start() {
        running.set(true);
    }

    @Override
    public void stop() {
        stop(() -> {});
    }

    @Override
    public void stop(Runnable callback) {
        if (running.compareAndSet(true, false)) {
            log.info("Draining in-flight webhook publishes (timeout {})…", drainTimeout);
            publisher.awaitInFlight(drainTimeout);
            log.info("Webhook publish drain complete.");
        }
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return PHASE;
    }
}
