package de.tum.in.www1.hephaestus.agent.mentor.chat;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;

/**
 * Executors for the mentor chat turn pipeline. Each one is owned by Spring and shut down on
 * application close.
 *
 * <ul>
 *   <li>{@code mentorTurnExecutor}: virtual-thread-per-task. The MVC dispatcher returns the
 *       emitter immediately; turn work runs here so Tomcat worker threads stay free.</li>
 *   <li>{@code mentorRunnerTimeoutScheduler}: shared scheduled pool for runner JSON-RPC
 *       deadlines. Sized at 2 daemon threads — one is enough for the JSON-RPC deadline
 *       firings (sub-microsecond CompletableFuture completes, no blocking work), the second
 *       services {@code MentorSseChannel}'s comment-only heartbeat ticks without head-of-line
 *       blocking when a deadline fires at the same instant.</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "hephaestus.mentor.enabled", havingValue = "true")
public class MentorChatExecutorConfig {

    private static final Logger log = LoggerFactory.getLogger(MentorChatExecutorConfig.class);

    @Bean(destroyMethod = "")
    MentorTurnExecutor mentorTurnExecutor() {
        // Production wrapping: the vthread executor is decorated with
        // DelegatingSecurityContextExecutorService so the request thread's authentication
        // is available inside `runTurn` (and any future call from this executor that needs
        // SecurityContextHolder).
        return new MentorTurnExecutor(Executors.newVirtualThreadPerTaskExecutor(), true);
    }

    @Bean(destroyMethod = "")
    MentorRunnerTimeoutScheduler mentorRunnerTimeoutScheduler() {
        return new MentorRunnerTimeoutScheduler();
    }

    /**
     * Virtual-thread executor for mentor turns, wrapped with
     * {@link DelegatingSecurityContextExecutorService} so the request thread's
     * {@code SecurityContext} propagates to the vthread. Without the wrapper,
     * {@code SecurityContextHolder} (MODE_THREADLOCAL) returns an empty context on every
     * vthread, and {@code userRepository.getCurrentUserElseThrow()} masks as a turn failure.
     *
     * <p>Implements {@link DisposableBean} so Spring shuts the underlying raw executor down
     * gracefully on context refresh / app stop — the wrapper itself doesn't expose lifecycle.
     */
    static final class MentorTurnExecutor implements DisposableBean {

        private final ExecutorService rawDelegate;
        private final ExecutorService delegate;

        MentorTurnExecutor() {
            this(Executors.newVirtualThreadPerTaskExecutor());
        }

        /**
         * Package-private test seam — let tests inject a synchronous executor without
         * reflection. Tests run on the main thread which already carries the test
         * {@code SecurityContext}, so we deliberately skip the propagation wrapper there to
         * keep the test surface simple.
         */
        MentorTurnExecutor(ExecutorService delegate) {
            this.rawDelegate = delegate;
            this.delegate = delegate;
        }

        /**
         * Production-only secondary constructor: wraps the raw executor with the Spring
         * Security propagation decorator. Kept separate from the default constructor so the
         * test seam above remains a bare delegate.
         */
        private MentorTurnExecutor(ExecutorService raw, boolean propagateSecurityContext) {
            this.rawDelegate = raw;
            this.delegate = propagateSecurityContext ? new DelegatingSecurityContextExecutorService(raw) : raw;
        }

        public ExecutorService executor() {
            return delegate;
        }

        @PreDestroy
        @Override
        public void destroy() {
            // Shut down the RAW executor directly — DelegatingSecurityContextExecutorService
            // delegates lifecycle, but going through the raw reference makes ownership
            // explicit and removes a layer of indirection from shutdown reasoning.
            rawDelegate.shutdown();
            try {
                if (!rawDelegate.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("mentorTurnExecutor did not terminate within 10s; forcing shutdown");
                    rawDelegate.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                rawDelegate.shutdownNow();
            }
        }
    }

    /** Shared scheduled pool for runner JSON-RPC timeouts. Bound to JVM lifecycle. */
    static final class MentorRunnerTimeoutScheduler implements DisposableBean {

        private final ScheduledExecutorService delegate;

        MentorRunnerTimeoutScheduler() {
            ThreadFactory tf = new ThreadFactory() {
                private final AtomicInteger n = new AtomicInteger();

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "mentor-runner-timeout-" + n.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            };
            this.delegate = Executors.newScheduledThreadPool(2, tf);
        }

        /** Package-private test seam — let tests inject a deterministic single-thread scheduler. */
        MentorRunnerTimeoutScheduler(ScheduledExecutorService delegate) {
            this.delegate = delegate;
        }

        public ScheduledExecutorService scheduler() {
            return delegate;
        }

        @PreDestroy
        @Override
        public void destroy() {
            delegate.shutdownNow();
        }
    }
}
