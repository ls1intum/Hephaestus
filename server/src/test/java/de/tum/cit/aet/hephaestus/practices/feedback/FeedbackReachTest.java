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
     * reach that closed it would leave nowhere at all — that is what the autonomy tier's {@code OFF} is for,
     * on the other axis.
     */
    @ParameterizedTest
    @EnumSource(FeedbackReach.class)
    void everyReachKeepsTheMentorConversationOpen(FeedbackReach reach) {
        assertThat(reach.reaches(FeedbackChannel.CONVERSATION)).isTrue();
    }

    /**
     * PROFILE is a declared channel with no producer anywhere in {@code src/main}. This is the guard: when
     * a PROFILE producer is built, this fails, and whoever builds it decides deliberately which reaches
     * include it — rather than a reach silently claiming a delivery the code cannot perform.
     */
    @ParameterizedTest
    @EnumSource(FeedbackReach.class)
    void noReachClaimsTheUnwrittenProfileChannel(FeedbackReach reach) {
        assertThat(reach.reaches(FeedbackChannel.PROFILE)).isFalse();
    }

    /** The column and its check constraint are sized from this; a longer constant would truncate. */
    @Test
    void everyConstantFitsTheColumn() {
        assertThat(Arrays.stream(FeedbackReach.values()).map(Enum::name).map(String::length)).allSatisfy(length ->
            assertThat(length).isLessThanOrEqualTo(FeedbackReach.MAX_LENGTH)
        );
    }

    /** A workspace that has never chosen keeps doing what every practice already did. */
    @Test
    void theFallbackIsWhatEveryWorkspaceAlreadyDid() {
        assertThat(FeedbackReach.DEFAULT).isEqualTo(FeedbackReach.ON_THE_WORK);
    }
}
