package de.tum.cit.aet.hephaestus.core.runtime.hub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.CancelJob;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.WorkerControlFrame;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@DisplayName("WorkerJobCancelDispatcher")
class WorkerJobCancelDispatcherTest extends BaseUnitTest {

    @Mock
    private WorkerSessionRegistry registry;

    @Mock
    private WorkerSession session;

    private WorkerJobCancelDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new WorkerJobCancelDispatcher(registry);
    }

    @Test
    @DisplayName("sends a CancelJob to the owning worker's live session")
    void dispatchesToLiveOwner() {
        UUID jobId = UUID.randomUUID();
        when(registry.findByWorkerId("worker-a")).thenReturn(Optional.of(session));

        boolean dispatched = dispatcher.dispatch("worker-a", jobId, "user-cancel");

        assertThat(dispatched).isTrue();
        ArgumentCaptor<WorkerControlFrame> frame = ArgumentCaptor.forClass(WorkerControlFrame.class);
        verify(session).send(frame.capture());
        assertThat(frame.getValue()).isInstanceOf(CancelJob.class);
        CancelJob cancel = (CancelJob) frame.getValue();
        assertThat(cancel.jobId()).isEqualTo(jobId.toString());
        assertThat(cancel.reason()).isEqualTo("user-cancel");
    }

    @Test
    @DisplayName("returns false when the owning worker is not connected to this hub")
    void noLiveSessionReturnsFalse() {
        when(registry.findByWorkerId("worker-gone")).thenReturn(Optional.empty());

        boolean dispatched = dispatcher.dispatch("worker-gone", UUID.randomUUID(), "user-cancel");

        assertThat(dispatched).isFalse();
    }

    @Test
    @DisplayName("returns false (no lookup) for a null/blank workerId")
    void nullWorkerIdReturnsFalse() {
        assertThat(dispatcher.dispatch(null, UUID.randomUUID(), "x")).isFalse();
        assertThat(dispatcher.dispatch("  ", UUID.randomUUID(), "x")).isFalse();
        verify(registry, never()).findByWorkerId(any());
    }
}
