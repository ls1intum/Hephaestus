package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
 * the loop rather than of any row, and which the previous shape got wrong: one {@code @Transactional}
 * spanning every row meant one optimistic-lock collision discarded the ledger writes of every turn
 * already billed in that pass, and left all of them stuck {@code in_flight} behind the partial unique
 * index.
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
        assertThat(lock.lockAtMostFor()).as("an unbounded lock survives a crashed pod").isNotBlank();
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
