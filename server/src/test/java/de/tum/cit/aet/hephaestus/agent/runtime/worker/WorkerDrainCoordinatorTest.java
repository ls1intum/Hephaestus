package de.tum.cit.aet.hephaestus.agent.runtime.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobCancellationReason;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobExecutor;
import de.tum.cit.aet.hephaestus.agent.runtime.worker.session.WorkerSessionDispatcher;
import de.tum.cit.aet.hephaestus.agent.runtime.worker.testing.CapturingPublisher;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.CapacityReport;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.Heartbeat;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionCloseReason;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.WorkerControlFrame;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

class WorkerDrainCoordinatorTest extends BaseUnitTest {

    @Test
    void gracefulDrainAwaitsThenSucceeds() {
        WorkerProperties props = propsWithDrain(Duration.ofSeconds(5));
        WorkerCapacityState state = new WorkerCapacityState(props);
        CapturingPublisher publisher = new CapturingPublisher();
        AgentJobExecutor executor = mock(AgentJobExecutor.class);
        when(executor.awaitInFlight(any(Duration.class))).thenReturn(true);
        WorkerSessionDispatcher sessions = mock(WorkerSessionDispatcher.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

        WorkerDrainCoordinator coordinator = new WorkerDrainCoordinator(
            publisher,
            state,
            props,
            Optional.of(executor),
            Optional.of(sessions),
            events,
            new SimpleMeterRegistry()
        );
        coordinator.start();
        coordinator.stop();

        verify(executor).stopAcceptingNewJobs();
        verify(executor).awaitInFlight(Duration.ofSeconds(5));
        verify(executor, never()).cancelInFlight(any());
        verify(sessions).closeAll(SessionCloseReason.WORKER_DRAINING);
        assertThat(publisher.sent)
            .extracting(WorkerControlFrame::getClass)
            .containsExactly(Heartbeat.class, CapacityReport.class);
        assertThat(((Heartbeat) publisher.sent.get(0)).draining()).isTrue();
        assertThat(((CapacityReport) publisher.sent.get(1)).spareReview()).isZero();
        assertThat(coordinator.isDraining()).isTrue();
        verifyReadinessStateRefusingTrafficWasPublished(events);
    }

    @Test
    void gracefulOverrunCancelsRemaining() {
        WorkerProperties props = propsWithDrain(Duration.ofSeconds(5));
        AgentJobExecutor executor = mock(AgentJobExecutor.class);
        when(executor.awaitInFlight(any(Duration.class))).thenReturn(false);

        WorkerDrainCoordinator coordinator = new WorkerDrainCoordinator(
            new CapturingPublisher(),
            new WorkerCapacityState(props),
            props,
            Optional.of(executor),
            Optional.of(mock(WorkerSessionDispatcher.class)),
            mock(ApplicationEventPublisher.class),
            new SimpleMeterRegistry()
        );
        coordinator.start();
        coordinator.stop();

        verify(executor).cancelInFlight(AgentJobCancellationReason.DRAIN_GRACEFUL);
    }

    @Test
    void immediateDrainSkipsAwait() {
        WorkerProperties props = propsWithDrain(Duration.ZERO);
        AgentJobExecutor executor = mock(AgentJobExecutor.class);

        WorkerDrainCoordinator coordinator = new WorkerDrainCoordinator(
            new CapturingPublisher(),
            new WorkerCapacityState(props),
            props,
            Optional.of(executor),
            Optional.of(mock(WorkerSessionDispatcher.class)),
            mock(ApplicationEventPublisher.class),
            new SimpleMeterRegistry()
        );
        coordinator.start();
        coordinator.stop();

        verify(executor).stopAcceptingNewJobs();
        verify(executor, never()).awaitInFlight(any());
        verify(executor, times(1)).cancelInFlight(AgentJobCancellationReason.DRAIN_IMMEDIATE);
    }

    @Test
    void drainIsIdempotent() {
        WorkerProperties props = propsWithDrain(Duration.ofSeconds(1));
        AgentJobExecutor executor = mock(AgentJobExecutor.class);
        when(executor.awaitInFlight(any(Duration.class))).thenReturn(true);

        WorkerDrainCoordinator coordinator = new WorkerDrainCoordinator(
            new CapturingPublisher(),
            new WorkerCapacityState(props),
            props,
            Optional.of(executor),
            Optional.of(mock(WorkerSessionDispatcher.class)),
            mock(ApplicationEventPublisher.class),
            new SimpleMeterRegistry()
        );
        coordinator.start();
        coordinator.stop();
        coordinator.stop(); // re-entrancy

        verify(executor, times(1)).stopAcceptingNewJobs();
    }

    @Test
    void availabilityEventEmittedEvenWhenSendsThrow() {
        WorkerProperties props = propsWithDrain(Duration.ZERO);
        WorkerControlPublisher throwing = new WorkerControlPublisher() {
            @Override
            public void send(WorkerControlFrame frame) {
                throw new RuntimeException("broken transport");
            }

            @Override
            public boolean isConnected() {
                return false;
            }

            @Override
            public Instant lastInboundAt() {
                return Instant.EPOCH;
            }
        };
        AgentJobExecutor executor = mock(AgentJobExecutor.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

        WorkerDrainCoordinator coordinator = new WorkerDrainCoordinator(
            throwing,
            new WorkerCapacityState(props),
            props,
            Optional.of(executor),
            Optional.empty(),
            events,
            new SimpleMeterRegistry()
        );
        coordinator.start();
        coordinator.stop();

        // Critical: even with a broken transport, the executor is still drained AND readiness
        // was flipped (kubelet would otherwise keep routing to a worker that's mid-shutdown).
        verify(executor).cancelInFlight(AgentJobCancellationReason.DRAIN_IMMEDIATE);
        verifyReadinessStateRefusingTrafficWasPublished(events);
    }

    private static WorkerProperties propsWithDrain(Duration timeout) {
        return de.tum.cit.aet.hephaestus.agent.runtime.worker.testing.WorkerPropertiesFixtures.withDrain(timeout);
    }

    private static void verifyReadinessStateRefusingTrafficWasPublished(ApplicationEventPublisher events) {
        ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(events, atLeastOnce()).publishEvent(captor.capture());
        boolean found = captor
            .getAllValues()
            .stream()
            .anyMatch(
                e -> e instanceof AvailabilityChangeEvent<?> a && a.getState() == ReadinessState.REFUSING_TRAFFIC
            );
        assertThat(found).as("expected an AvailabilityChangeEvent(REFUSING_TRAFFIC) to be published").isTrue();
    }
}
