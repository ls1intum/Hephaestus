package de.tum.cit.aet.hephaestus.agent.job.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Deterministic gate logic for conversation-thread detection: quiescence, depth, growth, and ts parsing. */
class ConversationThreadTriggerSchedulerTest extends BaseUnitTest {

    private static final int QUIESCENCE_MIN = 10;
    private static final int MIN_TURNS = 4;
    private static final int MIN_GROWTH = 2;

    /** A Slack ts (seconds.micro) whose second part is {@code now - ageSeconds}. */
    private static String tsAgedBy(Instant now, long ageSeconds) {
        return (now.getEpochSecond() - ageSeconds) + ".123456";
    }

    @Nested
    class Quiescence {

        @Test
        void notYetSettledIsRejected() {
            Instant now = Instant.now();
            // Last message 2 minutes ago — still inside the 10-minute quiescence window.
            String lastTs = tsAgedBy(now, Duration.ofMinutes(2).toSeconds());
            assertThat(
                ConversationThreadTriggerScheduler.passesGates(now, lastTs, 8, 5, QUIESCENCE_MIN, MIN_TURNS, MIN_GROWTH)
            ).isFalse();
        }

        @Test
        void settledThreadPasses() {
            Instant now = Instant.now();
            String lastTs = tsAgedBy(now, Duration.ofMinutes(15).toSeconds());
            assertThat(
                ConversationThreadTriggerScheduler.passesGates(now, lastTs, 8, 5, QUIESCENCE_MIN, MIN_TURNS, MIN_GROWTH)
            ).isTrue();
        }

        @Test
        void unparseableOrNullLastTsIsRejected() {
            Instant now = Instant.now();
            assertThat(
                ConversationThreadTriggerScheduler.passesGates(now, null, 8, 5, QUIESCENCE_MIN, MIN_TURNS, MIN_GROWTH)
            ).isFalse();
            assertThat(
                ConversationThreadTriggerScheduler.passesGates(
                    now,
                    "not-a-ts",
                    8,
                    5,
                    QUIESCENCE_MIN,
                    MIN_TURNS,
                    MIN_GROWTH
                )
            ).isFalse();
        }
    }

    @Nested
    class Depth {

        @Test
        void tooFewTurnsIsRejectedEvenWhenSettledAndGrown() {
            Instant now = Instant.now();
            String lastTs = tsAgedBy(now, Duration.ofMinutes(15).toSeconds());
            assertThat(
                ConversationThreadTriggerScheduler.passesGates(now, lastTs, 3, 3, QUIESCENCE_MIN, MIN_TURNS, MIN_GROWTH)
            ).isFalse();
        }
    }

    @Nested
    class Growth {

        @Test
        void noGrowthSinceWatermarkIsRejected() {
            // A settled, deep thread that has not grown past the watermark (late-reply / re-sweep) does not re-fire.
            Instant now = Instant.now();
            String lastTs = tsAgedBy(now, Duration.ofMinutes(15).toSeconds());
            assertThat(
                ConversationThreadTriggerScheduler.passesGates(now, lastTs, 8, 1, QUIESCENCE_MIN, MIN_TURNS, MIN_GROWTH)
            ).isFalse();
        }

        @Test
        void enoughGrowthPasses() {
            Instant now = Instant.now();
            String lastTs = tsAgedBy(now, Duration.ofMinutes(15).toSeconds());
            assertThat(
                ConversationThreadTriggerScheduler.passesGates(now, lastTs, 8, 2, QUIESCENCE_MIN, MIN_TURNS, MIN_GROWTH)
            ).isTrue();
        }
    }

    @Nested
    class SlackTsParsing {

        @Test
        void parsesSecondsPart() {
            assertThat(ConversationThreadTriggerScheduler.slackTsEpochSeconds("1700000000.123456")).isEqualTo(
                1700000000L
            );
        }

        @Test
        void parsesIntegerOnlyTs() {
            assertThat(ConversationThreadTriggerScheduler.slackTsEpochSeconds("1700000000")).isEqualTo(1700000000L);
        }

        @Test
        void returnsNullForNullBlankOrGarbage() {
            assertThat(ConversationThreadTriggerScheduler.slackTsEpochSeconds(null)).isNull();
            assertThat(ConversationThreadTriggerScheduler.slackTsEpochSeconds("  ")).isNull();
            assertThat(ConversationThreadTriggerScheduler.slackTsEpochSeconds("abc.def")).isNull();
        }
    }
}
