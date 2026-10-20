package de.tum.cit.aet.hephaestus.agent.mentor.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.job.ReviewJobExecutor;
import de.tum.cit.aet.hephaestus.agent.mentor.sandbox.TurnSandboxes;
import de.tum.cit.aet.hephaestus.agent.mentor.turn.MentorTurnCompletion;
import de.tum.cit.aet.hephaestus.agent.mentor.turn.MentorTurnStarter;
import de.tum.cit.aet.hephaestus.agent.proxy.ProxyCallsInFlight;
import de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerCapacityState;
import de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerControlClient;
import de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerDrainCoordinator;
import de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerProperties;
import de.tum.cit.aet.hephaestus.agent.runtime.worker.testing.WorkerPropertiesFixtures;
import de.tum.cit.aet.hephaestus.agent.sandbox.spi.HostSandbox;
import de.tum.cit.aet.hephaestus.core.runtime.hub.auth.WorkerJwtIssuer;
import de.tum.cit.aet.hephaestus.mentor.MentorLedgerNotifications;
import de.tum.cit.aet.hephaestus.mentor.MentorTurnService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.DefaultLifecycleProcessor;
import org.springframework.context.support.GenericApplicationContext;
import tools.jackson.databind.json.JsonMapper;

/**
 * The worker's review drain and Heph drain share one shutdown phase. Spring's lifecycle processor stops each bean of a
 * phase in turn and then waits for all their callbacks, so the two drains overlap only when neither waits inside
 * {@code stop(Runnable)}. Every wait here is a latch the test opens.
 */
class HephTurnDispatcherShutdownTest extends BaseUnitTest {

    /** Opens every drain the test holds. */
    private final CountDownLatch release = new CountDownLatch(1);

    /** Counted once the Heph drain removes the host; it then waits for {@link #release}. */
    private final CountDownLatch hephWaits = new CountDownLatch(1);

    private final AtomicBoolean hephEnded = new AtomicBoolean();

    /** A host whose removal is the end of the Heph drain, held until the test releases it. */
    private final class HeldHost extends HephHost {
        HeldHost() {
            super(mock(HostSandbox.class));
        }

        @Override
        void shutdown() {
            hephWaits.countDown();
            try {
                hephEnded.set(release.await(10, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void shouldDrainReviewsAndHephTurnsAlongsideEachOtherBeforeTheWebServerPhaseWhenTheWorkerContextCloses()
            throws Exception {
        CountDownLatch reviewWaits = new CountDownLatch(1);
        AtomicBoolean reviewEnded = new AtomicBoolean();
        ReviewJobExecutor executor = mock(ReviewJobExecutor.class);
        when(executor.awaitInFlight(any(Duration.class))).thenAnswer(invocation -> {
            reviewWaits.countDown();
            reviewEnded.set(release.await(10, TimeUnit.SECONDS));
            return reviewEnded.get();
        });
        WorkerProperties properties = WorkerPropertiesFixtures.withDrain(Duration.ofMinutes(5));
        var coordinator = new WorkerDrainCoordinator(
                mock(WorkerControlClient.class),
                new WorkerCapacityState(properties),
                properties,
                Optional.of(executor),
                mock(ApplicationEventPublisher.class),
                new SimpleMeterRegistry());
        HephTurnDispatcher dispatcher = dispatcher();
        var webServer = new WebServerPhase(() -> reviewEnded.get() && hephEnded.get());

        var context = new GenericApplicationContext();
        var lifecycle = new DefaultLifecycleProcessor();
        lifecycle.setBeanFactory(context.getBeanFactory());
        lifecycle.setTimeoutPerShutdownPhase(Duration.ofMinutes(1).toMillis());
        context.getBeanFactory().registerSingleton(AbstractApplicationContext.LIFECYCLE_PROCESSOR_BEAN_NAME, lifecycle);
        context.getBeanFactory().registerSingleton("workerDrainCoordinator", coordinator);
        context.getBeanFactory().registerSingleton("hephTurnDispatcher", dispatcher);
        context.getBeanFactory().registerSingleton("webServerPhase", webServer);
        context.refresh();
        assertThat(coordinator.isRunning()).isTrue();
        assertThat(dispatcher.isRunning()).isTrue();

        Thread closing = Thread.ofPlatform().name("worker-close").daemon(true).unstarted(context::close);
        try {
            closing.start();

            // Whichever drain Spring stops first, the other is stopped while the first still waits.
            assertThat(reviewWaits.await(10, TimeUnit.SECONDS))
                    .as("the review drain waits for the review in flight")
                    .isTrue();
            assertThat(hephWaits.await(10, TimeUnit.SECONDS))
                    .as("the Heph drain ends its turns while the review drain still waits")
                    .isTrue();
            verify(executor).stopAcceptingNewJobs();
            assertThat(dispatcher.isRunning()).as("Heph claims no turn").isFalse();
            assertThat(closing.isAlive()).as("the phase waits for both drains").isTrue();
            assertThat(webServer.isRunning())
                    .as("the web server still serves the gateway")
                    .isTrue();

            release.countDown();
            assertThat(closing.join(Duration.ofSeconds(10))).isTrue();
            assertThat(webServer.drainsEndedFirst).isTrue();
        } finally {
            release.countDown();
            if (closing.isAlive()) {
                closing.join(Duration.ofSeconds(10));
            }
        }
    }

    @Test
    void shouldStopClaimingAtOnceAndCallBackOnceWhenTheHostIsRemovedWhenStoppedWithACallback() throws Exception {
        HephTurnDispatcher dispatcher = dispatcher();
        dispatcher.start();
        AtomicInteger calledBack = new AtomicInteger();
        CountDownLatch ended = new CountDownLatch(1);

        dispatcher.stop(() -> {
            calledBack.incrementAndGet();
            ended.countDown();
        });

        assertThat(dispatcher.isRunning()).isFalse();
        assertThat(hephWaits.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(ended.getCount()).as("the drain still removes the host").isEqualTo(1);
        release.countDown();
        assertThat(ended.await(10, TimeUnit.SECONDS)).isTrue();
        dispatcher.stop(calledBack::incrementAndGet);
        assertThat(calledBack.get()).isEqualTo(2);
        assertThat(hephEnded.get()).isTrue();
    }

    @Test
    void shouldRemoveTheHostBeforeADirectStopReturns() {
        HephTurnDispatcher dispatcher = dispatcher();
        dispatcher.start();
        release.countDown();

        dispatcher.stop();

        assertThat(dispatcher.isRunning()).isFalse();
        assertThat(hephEnded.get()).isTrue();
    }

    private HephTurnDispatcher dispatcher() {
        return new HephTurnDispatcher(
                mock(MentorTurnStarter.class),
                mock(MentorTurnService.class),
                mock(MentorTurnCompletion.class),
                mock(MentorLedgerNotifications.class),
                new HeldHost(),
                new ProxyCallsInFlight(),
                mock(TurnSandboxes.class),
                mock(WorkerJwtIssuer.class),
                new JsonMapper(),
                Clock.systemUTC(),
                new SimpleMeterRegistry(),
                "worker",
                1,
                Duration.ofMinutes(1),
                HephTurnDispatcher.STOP_GRACE);
    }

    /** Stands in for the web server's graceful shutdown, the phase below the drains. */
    private static final class WebServerPhase implements SmartLifecycle {
        private final BooleanSupplier drainsEnded;
        private volatile boolean running;
        volatile boolean drainsEndedFirst;

        WebServerPhase(BooleanSupplier drainsEnded) {
            this.drainsEnded = drainsEnded;
        }

        @Override
        public void start() {
            running = true;
        }

        @Override
        public void stop() {
            drainsEndedFirst = drainsEnded.getAsBoolean();
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public int getPhase() {
            return WebServerApplicationContext.GRACEFUL_SHUTDOWN_PHASE;
        }
    }
}
