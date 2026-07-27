package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.config.AgentBindingLimits;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.mentor.ChatMessage;
import de.tum.cit.aet.hephaestus.mentor.ChatMessageRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * The reaper's SWEEP contract: what one bad row is allowed to do to the rest of the batch.
 *
 * <p>Deliberately not about what a single turn is billed — {@code MentorTurnPersistenceIntegrationTest}
 * owns that against a real database. What is asserted here is the blast radius, which is a property of
 * the loop rather than of any row: a single {@code @Transactional} spanning every row would let one
 * optimistic-lock collision discard the ledger writes of every turn already billed in that pass and
 * leave all of them stuck {@code in_flight} behind the partial unique index, so the boundary has to sit
 * around one turn.
 *
 * <p>{@code self} is injected, so the per-turn transactional boundary is a seam a unit test can push
 * on: making it throw is exactly the collision the production proxy would surface.
 */
class MentorInFlightReaperTest extends BaseUnitTest {

    private final ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
    private final LlmUsageRecorder usageRecorder = mock(LlmUsageRecorder.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    @DisplayName("one turn's failed write does not stop the turns behind it")
    void shouldKeepAccountingAfterOneTurnFails() {
        MentorInFlightReaper self = mock(MentorInFlightReaper.class);
        List<ChatMessage> stale = List.of(messageWithId(), messageWithId(), messageWithId());
        UUID first = stale.get(0).getId();
        UUID second = stale.get(1).getId();
        UUID third = stale.get(2).getId();
        when(chatMessageRepository.findStaleInFlightForAccounting(any())).thenReturn(stale);
        // The turn finished between the select and the write; its snapshot is stale and the write loses.
        when(self.accountOne(second)).thenThrow(new OptimisticLockingFailureException("row moved"));
        when(self.accountOne(first)).thenReturn(true);
        when(self.accountOne(third)).thenReturn(true);

        MentorInFlightReaper reaper = reaperWith(self);

        assertThatCode(reaper::reap).doesNotThrowAnyException();
        // The point: the row AFTER the failure is still reached. Delete the per-row try/catch and this
        // is where the test fails — third is never attempted and the exception escapes the scheduler.
        verify(self).accountOne(third);
        assertThat(meterRegistry.counter("mentor.in_flight.reaper.failure").count())
            .as("a lost write must be counted, not swallowed — it means a turn is staying stuck")
            .isEqualTo(1.0);
    }

    @Test
    @DisplayName("a turn that left in_flight since the select is skipped, not billed again")
    void shouldSkipTurnThatIsNoLongerInFlight() {
        ChatMessage finished = messageWithId();
        finished.setStatus(ChatMessage.Status.completed);
        when(chatMessageRepository.findById(finished.getId())).thenReturn(Optional.of(finished));

        MentorInFlightReaper reaper = reaperWith(mock(MentorInFlightReaper.class));

        assertThat(reaper.accountOne(finished.getId())).isFalse();
        // Its own completion path already billed it. Drop the status re-check and the reaper bills a
        // second ledger event for the same turn.
        verify(usageRecorder, never()).record(anyLong(), any());
        verify(usageRecorder, never()).recordUnverifiable(anyLong(), any());
    }

    @Test
    @DisplayName("a turn deleted since the select is skipped rather than NPEing the sweep")
    void shouldSkipTurnThatNoLongerExists() {
        UUID gone = UUID.randomUUID();
        when(chatMessageRepository.findById(gone)).thenReturn(Optional.empty());

        MentorInFlightReaper reaper = reaperWith(mock(MentorInFlightReaper.class));

        assertThat(reaper.accountOne(gone)).isFalse();
        verify(usageRecorder, never()).record(anyLong(), any());
    }

    /**
     * The sweep writes money, so two replicas must not both run it. Asserted on the annotation because
     * that IS the mechanism — there is no observable behaviour to assert without a second JVM, and
     * every other ledger-writing sweep in the tree carries the same one.
     */
    @Test
    @DisplayName("the sweep is single-flighted across replicas")
    void shouldBeSchedulerLocked() throws NoSuchMethodException {
        Method reap = MentorInFlightReaper.class.getMethod("reap");

        SchedulerLock lock = reap.getAnnotation(SchedulerLock.class);

        assertThat(lock).as("an unlocked money-writing sweep double-runs on every multi-replica deploy").isNotNull();
        assertThat(lock.name()).isEqualTo("mentor-in-flight-reaper");
        assertThat(lock.lockAtMostFor())
            .as(
                "without its own bound the sweep falls back to ShedLockConfig's 30-minute default, so a crashed pod " +
                    "holds the lock across far more of this two-minute sweep's ticks than its own runtime warrants"
            )
            .isNotBlank();
    }

    /**
     * The sweep's one assumption about the outside world: a turn older than the window cannot still be
     * running. That is only true because a binding's per-run timeout has an enforced ceiling
     * ({@link AgentBindingLimits#MAX_TIMEOUT_SECONDS}, refused above by the binding API and clamped to
     * by {@code MentorPiAdapter}) and this window is sized from it. Reaping a live turn bills it as
     * abandoned and closes a conversation someone is talking to, so the two must be checked against
     * each other rather than each against a literal.
     *
     * <p>Fails if the derivation is replaced by a constant and the ceiling is later raised, or if the
     * floor stops being applied to a configured window that is too small.
     */
    @Test
    @DisplayName("the window always outlasts the longest turn a binding can be configured to produce")
    void shouldNeverReapWithinTheConfigurableTimeoutCeiling() {
        Duration longestPossibleTurn = Duration.ofSeconds(AgentBindingLimits.MAX_TIMEOUT_SECONDS);

        MentorInFlightReaper defaultWindow = reaperWith(mock(MentorInFlightReaper.class));
        // An operator lowering the property below the safe floor gets the floor, not their value:
        // hephaestus.mentor.in-flight-reaper.window is a knob for sweeping LATER, never sooner.
        MentorInFlightReaper misconfigured = new MentorInFlightReaper(
            chatMessageRepository,
            usageRecorder,
            meterRegistry,
            mock(MentorInFlightReaper.class),
            Duration.ofMinutes(1)
        );

        assertThat(defaultWindow.window()).isGreaterThan(longestPossibleTurn);
        assertThat(misconfigured.window())
            .as("a window under the timeout ceiling reaps turns that are still streaming")
            .isGreaterThan(longestPossibleTurn);
    }

    private MentorInFlightReaper reaperWith(MentorInFlightReaper self) {
        return new MentorInFlightReaper(
            chatMessageRepository,
            usageRecorder,
            meterRegistry,
            self,
            Duration.ofMinutes(70)
        );
    }

    private static ChatMessage messageWithId() {
        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID());
        message.setStatus(ChatMessage.Status.in_flight);
        return message;
    }
}
