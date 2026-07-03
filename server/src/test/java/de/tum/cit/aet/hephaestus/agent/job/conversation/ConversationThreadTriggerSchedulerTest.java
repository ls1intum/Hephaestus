package de.tum.cit.aet.hephaestus.agent.job.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Deterministic gate logic for conversation-thread detection: quiescence, depth, growth, and ts parsing. */
class ConversationThreadTriggerSchedulerTest extends BaseUnitTest {

    private static final int QUIESCENCE_MIN = 10;
    private static final int MIN_TURNS = 4;
    private static final int MIN_GROWTH = 2;

    /** A Slack ts (seconds.micro) whose second part is {@code now - ageSeconds}. */
    private static String tsAgedBy(Instant now, long ageSeconds) {
        return (now.getEpochSecond() - ageSeconds) + ".123456";
    }

    @ParameterizedTest
    @CsvSource(
        {
            // ageMin, turns, growthSinceWatermark, expectedPass
            "2, 8, 5, false", // still inside the 10-minute quiescence window
            "15, 8, 5, true", // settled + deep + grown
            "15, 3, 3, false", // too few turns even when settled and grown
            "15, 8, 1, false", // no growth past the watermark (late-reply / re-sweep) does not re-fire
            "15, 8, 2, true", // exactly enough growth
        }
    )
    void passesGatesEvaluatesQuiescenceDepthAndGrowth(int ageMin, int turns, int growth, boolean expected) {
        Instant now = Instant.now();
        String lastTs = tsAgedBy(now, Duration.ofMinutes(ageMin).toSeconds());
        assertThat(
            ConversationThreadTriggerScheduler.passesGates(
                now,
                lastTs,
                turns,
                growth,
                QUIESCENCE_MIN,
                MIN_TURNS,
                MIN_GROWTH
            )
        ).isEqualTo(expected);
    }

    @Test
    void unparseableOrNullLastTsIsRejected() {
        Instant now = Instant.now();
        assertThat(
            ConversationThreadTriggerScheduler.passesGates(now, null, 8, 5, QUIESCENCE_MIN, MIN_TURNS, MIN_GROWTH)
        ).isFalse();
        assertThat(
            ConversationThreadTriggerScheduler.passesGates(now, "not-a-ts", 8, 5, QUIESCENCE_MIN, MIN_TURNS, MIN_GROWTH)
        ).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "1700000000.123456", "1700000000" })
    void slackTsEpochSecondsParsesSecondsPart(String ts) {
        assertThat(ConversationThreadTriggerScheduler.slackTsEpochSeconds(ts)).isEqualTo(1700000000L);
    }

    @Test
    void slackTsEpochSecondsReturnsNullForNullBlankOrGarbage() {
        assertThat(ConversationThreadTriggerScheduler.slackTsEpochSeconds(null)).isNull();
        assertThat(ConversationThreadTriggerScheduler.slackTsEpochSeconds("  ")).isNull();
        assertThat(ConversationThreadTriggerScheduler.slackTsEpochSeconds("abc.def")).isNull();
    }
}
