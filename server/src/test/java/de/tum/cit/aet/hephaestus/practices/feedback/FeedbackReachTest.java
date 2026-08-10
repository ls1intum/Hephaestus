package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Where a workspace lets feedback go — the axis the autonomy tier deliberately says nothing about. */
@DisplayName("Feedback reach")
class FeedbackReachTest extends BaseUnitTest {

    @Test
    void onlyReachingTheWorkPlacesFeedbackOnIt() {
        assertThat(FeedbackReach.CONVERSATION.reaches(FeedbackChannel.IN_CONTEXT)).isFalse();
        assertThat(FeedbackReach.ON_THE_WORK.reaches(FeedbackChannel.IN_CONTEXT)).isTrue();
    }

    /**
     * The mentor conversation is open at every reach. It is the narrowest place the system can speak, so a
     * reach that closed it would leave nowhere at all — which is what the autonomy tier's {@code OFF} and
     * {@code OBSERVE} are for, on the other axis.
     */
    @ParameterizedTest
    @EnumSource(FeedbackReach.class)
    void everyReachKeepsTheMentorConversationOpen(FeedbackReach reach) {
        assertThat(reach.reaches(FeedbackChannel.CONVERSATION)).isTrue();
    }

    /**
     * PROFILE is a declared channel with no producer anywhere in {@code src/main} — nothing ever writes a
     * PROFILE feedback unit. Reach is therefore documented and implemented as conversation-or-work rather
     * than as "the quiet channels", because claiming a delivery the code cannot perform is the failure
     * mode this contract exists to prevent. This test is the guard: when a PROFILE producer is built it
     * fails, and whoever builds it decides deliberately which reaches include it.
     */
    @ParameterizedTest
    @EnumSource(FeedbackReach.class)
    void noReachClaimsTheUnwrittenProfileChannel(FeedbackReach reach) {
        assertThat(reach.reaches(FeedbackChannel.PROFILE)).isFalse();
    }

    /** Reach is monotone: widening it never closes a channel that was already open. */
    @Test
    void wideningReachOnlyEverAdds() {
        for (FeedbackChannel channel : FeedbackChannel.values()) {
            if (FeedbackReach.CONVERSATION.reaches(channel)) {
                assertThat(FeedbackReach.ON_THE_WORK.reaches(channel))
                    .as("ON_THE_WORK must reach %s because CONVERSATION does", channel)
                    .isTrue();
            }
        }
    }

    /** The column and its check constraint are sized from this; a longer constant would truncate. */
    @Test
    void everyConstantFitsTheColumn() {
        assertThat(Arrays.stream(FeedbackReach.values()).map(Enum::name).map(String::length)).allSatisfy(length ->
            assertThat(length).isLessThanOrEqualTo(FeedbackReach.MAX_LENGTH)
        );
    }

    /**
     * A workspace that has never chosen keeps doing what every practice already did. Reach was folded into
     * the per-practice tier before this, and the shipped default reached the work.
     */
    @Test
    void theFallbackIsWhatEveryWorkspaceAlreadyDid() {
        assertThat(FeedbackReach.DEFAULT).isEqualTo(FeedbackReach.ON_THE_WORK);
    }
}
