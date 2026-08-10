package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackReach;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The whole delivery predicate: which measurements may be said out loud, where. */
@DisplayName("Feedback admission")
class FeedbackAdmissionTest extends BaseUnitTest {

    @Nested
    @DisplayName("Provenance")
    class Provenance {

        /**
         * Written as a derivation rather than as a constant.
         *
         * <p>A backfilled observation is entitled to PROFILE and to nothing else, and PROFILE has no
         * producer anywhere in {@code src/main} — so a backfill is measured and delivered nowhere.
         * Asserting "backfills produce no feedback" directly would state a decision; asserting it this
         * way makes it a consequence of two facts that are each independently true and independently
         * tested.
         *
         * <p><strong>This test fails the day a PROFILE producer appears.</strong> It cannot appear
         * without {@code FeedbackReach.reaches(PROFILE)} becoming true for some reach, which trips
         * {@code FeedbackReachTest.noReachClaimsTheUnwrittenProfileChannel} first. Whoever builds that
         * surface then has to decide deliberately whether a retrospective measurement belongs on it —
         * which is the decision this pins open rather than answers.
         */
        @Test
        void aBackfilledObservationReachesNoChannelThatAnyoneCanWriteToToday() {
            for (PracticeReviewTier tier : PracticeReviewTier.values()) {
                for (FeedbackReach reach : FeedbackReach.values()) {
                    for (FeedbackChannel channel : FeedbackChannel.values()) {
                        assertThat(FeedbackAdmission.delivers(ObservationOrigin.BACKFILL, tier, reach, channel))
                            .as("BACKFILL at tier %s, reach %s, on channel %s", tier, reach, channel)
                            .isFalse();
                    }
                }
            }
        }

        /** …and the reason is specifically PROFILE's, not a blanket refusal we would forget to revisit. */
        @Test
        void aBackfillIsEntitledToTheProfileChannelAndOnlyThat() {
            assertThat(ObservationOrigin.BACKFILL.delivers(FeedbackChannel.PROFILE)).isTrue();
            assertThat(ObservationOrigin.BACKFILL.delivers(FeedbackChannel.IN_CONTEXT)).isFalse();
            assertThat(ObservationOrigin.BACKFILL.delivers(FeedbackChannel.CONVERSATION)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = ObservationOrigin.class, names = { "LIVE", "MANUAL" })
        void aMeasurementOfWorkAsItHappenedLeavesEveryChannelToTheTier(ObservationOrigin origin) {
            assertThat(Arrays.stream(FeedbackChannel.values()).allMatch(origin::delivers)).isTrue();
        }
    }

    @Nested
    @DisplayName("Conjunction")
    class Conjunction {

        @Test
        void allThreeAxesMustAdmitAChannel() {
            // Everything says yes.
            assertThat(
                FeedbackAdmission.delivers(
                    ObservationOrigin.LIVE,
                    PracticeReviewTier.DELIVER,
                    FeedbackReach.ON_THE_WORK,
                    FeedbackChannel.IN_CONTEXT
                )
            ).isTrue();
            // The tier alone refuses: no autonomy to act on its own.
            assertThat(
                FeedbackAdmission.delivers(
                    ObservationOrigin.LIVE,
                    PracticeReviewTier.OBSERVE,
                    FeedbackReach.ON_THE_WORK,
                    FeedbackChannel.IN_CONTEXT
                )
            ).isFalse();
            // The reach alone refuses: this workspace does not speak on the work.
            assertThat(
                FeedbackAdmission.delivers(
                    ObservationOrigin.LIVE,
                    PracticeReviewTier.DELIVER,
                    FeedbackReach.CONVERSATION,
                    FeedbackChannel.IN_CONTEXT
                )
            ).isFalse();
            // The provenance alone refuses: a retrospective finding is not actionable on the work.
            assertThat(
                FeedbackAdmission.delivers(
                    ObservationOrigin.BACKFILL,
                    PracticeReviewTier.DELIVER,
                    FeedbackReach.ON_THE_WORK,
                    FeedbackChannel.IN_CONTEXT
                )
            ).isFalse();
        }

        /**
         * The two configurable axes are genuinely independent, which is the point of splitting them.
         * Narrowing reach must not silence a channel the tier still owns, and lowering the tier must not be
         * undoable by widening reach — the old single ladder could express neither, which is why its middle
         * rungs had no order.
         */
        @Test
        void reachCannotMakeAQuietPracticeSpeakAndTheTierCannotChooseWhere() {
            assertThat(
                FeedbackAdmission.delivers(
                    ObservationOrigin.LIVE,
                    PracticeReviewTier.OBSERVE,
                    FeedbackReach.ON_THE_WORK,
                    FeedbackChannel.CONVERSATION
                )
            ).isFalse();
            assertThat(
                FeedbackAdmission.delivers(
                    ObservationOrigin.LIVE,
                    PracticeReviewTier.DELIVER,
                    FeedbackReach.CONVERSATION,
                    FeedbackChannel.CONVERSATION
                )
            ).isTrue();
        }

        /**
         * An unresolved tier admits <em>on its own axis</em>, because withholding feedback a developer was
         * owed on the strength of a lookup miss is the worse failure. Neither of the other two rules has such
         * an escape hatch — both are known without a per-practice lookup, so a null tier cannot smuggle a
         * backfill, or a comment in a conversation-only workspace, onto the artifact.
         */
        @Test
        void anUnresolvedTierDoesNotUndoTheOtherTwoRules() {
            assertThat(
                FeedbackAdmission.delivers(
                    ObservationOrigin.LIVE,
                    null,
                    FeedbackReach.ON_THE_WORK,
                    FeedbackChannel.IN_CONTEXT
                )
            ).isTrue();
            assertThat(
                FeedbackAdmission.delivers(
                    ObservationOrigin.BACKFILL,
                    null,
                    FeedbackReach.ON_THE_WORK,
                    FeedbackChannel.IN_CONTEXT
                )
            ).isFalse();
            // Reach has no escape hatch either: it is a workspace-level fact, known without a per-practice
            // lookup, so a failed tier lookup cannot put a comment on the work of a workspace that asked us
            // never to comment on work.
            assertThat(
                FeedbackAdmission.delivers(
                    ObservationOrigin.LIVE,
                    null,
                    FeedbackReach.CONVERSATION,
                    FeedbackChannel.IN_CONTEXT
                )
            ).isFalse();
        }
    }
}
