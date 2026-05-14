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

/**
 * Executors for the mentor chat turn pipeline. Each one is owned by Spring and shut down on
 * application close.
 *
 * <ul>
 *   <li>{@code mentorTurnExecutor}: virtual-thread-per-task. The MVC dispatcher returns the
 *       emitter immediately; turn work runs here so Tomcat worker threads stay free.</li>
 *   <li>{@code mentorRunnerTimeoutScheduler}: shared scheduled pool for runner JSON-RPC
 *       deadlines. One thread per replica is plenty — every fire either races a fast path
 *       or completes the future exceptionally; no blocking work runs here.</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "hephaestus.mentor.enabled", havingValue = "true")
public class MentorChatExecutorConfig {

    private static final Logger log = LoggerFactory.getLogger(MentorChatExecutorConfig.class);

    @Bean(destroyMethod = "")
    MentorTurnExecutor mentorTurnExecutor() {
        return new MentorTurnExecutor();
    }

    @Bean(destroyMethod = "")
    MentorRunnerTimeoutScheduler mentorRunnerTimeoutScheduler() {
        return new MentorRunnerTimeoutScheduler();
    }

    /** Virtual-thread executor for mentor turns. Wrapped so {@link DisposableBean} can shut it down. */
    static final class MentorTurnExecutor implements DisposableBean {

        private final ExecutorService delegate;

        MentorTurnExecutor() {
            this(Executors.newVirtualThreadPerTaskExecutor());
        }

        /** Package-private test seam — let tests inject a synchronous executor without reflection. */
        MentorTurnExecutor(ExecutorService delegate) {
            this.delegate = delegate;
        }

        public ExecutorService executor() {
            return delegate;
        }

        @PreDestroy
        @Override
        public void destroy() {
            delegate.shutdown();
            try {
                if (!delegate.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("mentorTurnExecutor did not terminate within 10s; forcing shutdown");
                    delegate.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                delegate.shutdownNow();
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
