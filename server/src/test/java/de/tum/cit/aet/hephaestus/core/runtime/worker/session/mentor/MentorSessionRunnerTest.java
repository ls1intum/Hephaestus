package de.tum.cit.aet.hephaestus.core.runtime.worker.session.mentor;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.runtime.worker.WorkerCapacityState;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionClose;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionCloseReason;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionInput;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionKind;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionOpen;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionOutput;
import de.tum.cit.aet.hephaestus.core.runtime.worker.testing.CapturingPublisher;
import de.tum.cit.aet.hephaestus.core.runtime.worker.testing.WorkerPropertiesFixtures;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MentorSessionRunnerTest extends BaseUnitTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final CapturingPublisher publisher = new CapturingPublisher();
    private final WorkerCapacityState state = new WorkerCapacityState(WorkerPropertiesFixtures.minimal("2", "2"));
    private final MentorSessionRunner runner = new MentorSessionRunner(
        publisher,
        state,
        Optional.empty(),
        new ObjectMapper(),
        registry
    );

    @Test
    void openClaimsCapacityAndAcksWithInitialOutput() {
        runner.onOpen(openFor("s-1"));

        assertThat(state.snapshot().spareMentor()).isEqualTo(1);
        assertThat(publisher.sent)
            .singleElement()
            .satisfies(f -> {
                assertThat(f).isInstanceOf(SessionOutput.class);
                assertThat(((SessionOutput) f).terminal()).isFalse();
            });
        assertThat(registry.counter("worker.mentor.session.opened").count()).isEqualTo(1.0);
    }

    @Test
    void openAtCapacityRejectsWithClose() {
        WorkerCapacityState saturated = new WorkerCapacityState(WorkerPropertiesFixtures.minimal("1", "1"));
        saturated.tryClaimMentor();
        MentorSessionRunner constrained = new MentorSessionRunner(
            publisher,
            saturated,
            Optional.empty(),
            new ObjectMapper(),
            registry
        );

        constrained.onOpen(openFor("s-2"));

        assertThat(publisher.sent)
            .singleElement()
            .satisfies(f -> {
                assertThat(f).isInstanceOf(SessionClose.class);
                assertThat(((SessionClose) f).reason()).isEqualTo(SessionCloseReason.ERROR);
            });
        assertThat(registry.counter("worker.mentor.session.rejected").count()).isEqualTo(1.0);
    }

    @Test
    void closeReleasesCapacityAndEmitsTerminalOutputThenSessionClose() {
        runner.onOpen(openFor("s-1"));
        publisher.sent.clear();

        runner.onClose(new SessionClose("s-1", SessionCloseReason.COMPLETED));

        assertThat(state.snapshot().spareMentor()).isEqualTo(2);
        assertThat(publisher.sent).hasSize(2);
        assertThat(((SessionOutput) publisher.sent.get(0)).terminal()).isTrue();
        assertThat(((SessionClose) publisher.sent.get(1)).reason()).isEqualTo(SessionCloseReason.COMPLETED);
    }

    @Test
    void closeAllFanoutEndsEveryInFlightSession() {
        runner.onOpen(openFor("s-1"));
        runner.onOpen(openFor("s-2"));
        publisher.sent.clear();

        runner.closeAll(SessionCloseReason.WORKER_DRAINING);

        assertThat(runner.activeCount()).isZero();
        assertThat(state.snapshot().spareMentor()).isEqualTo(2);
        long closes = publisher.sent.stream()
            .filter(f -> f instanceof SessionClose c && c.reason() == SessionCloseReason.WORKER_DRAINING)
            .count();
        assertThat(closes).isEqualTo(2);
    }

    @Test
    void onInputForUnknownSessionIsNoOp() {
        runner.onInput(new SessionInput("never-opened", "{\"x\":1}"));
        assertThat(publisher.sent).isEmpty();
    }

    @Test
    void duplicateOpenReleasesSpeculativeClaim() {
        runner.onOpen(openFor("s-1"));
        runner.onOpen(openFor("s-1"));

        assertThat(state.snapshot().spareMentor()).isEqualTo(1);
    }

    private static SessionOpen openFor(String sessionId) {
        return new SessionOpen(sessionId, SessionKind.MENTOR_INTERACTIVE, new ObjectMapper().createObjectNode());
    }
}
