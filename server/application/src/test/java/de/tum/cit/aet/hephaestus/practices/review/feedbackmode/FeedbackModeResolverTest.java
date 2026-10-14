package de.tum.cit.aet.hephaestus.practices.review.feedbackmode;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackModeSource;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class FeedbackModeResolverTest extends BaseUnitTest {
    @Test
    void shouldUseTheNearestExplicitModeWhenResolved() {
        assertThat(FeedbackModeResolver.resolve(FeedbackMode.OFF, FeedbackMode.AUTOMATIC, FeedbackMode.APPROVE_FIRST))
                .isEqualTo(new EffectiveFeedbackMode(FeedbackMode.OFF, FeedbackModeSource.PRACTICE));
        assertThat(FeedbackModeResolver.resolve(null, FeedbackMode.OFF, FeedbackMode.AUTOMATIC))
                .isEqualTo(new EffectiveFeedbackMode(FeedbackMode.OFF, FeedbackModeSource.GROUP));
        assertThat(FeedbackModeResolver.resolve(null, null, FeedbackMode.AUTOMATIC))
                .isEqualTo(new EffectiveFeedbackMode(FeedbackMode.AUTOMATIC, FeedbackModeSource.WORKSPACE));
        assertThat(FeedbackModeResolver.resolve(null, null, null))
                .isEqualTo(new EffectiveFeedbackMode(FeedbackMode.APPROVE_FIRST, FeedbackModeSource.INSTANCE));
    }
}
