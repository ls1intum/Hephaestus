package de.tum.cit.aet.hephaestus.practices.review.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.spi.ReviewCadence;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewScheduleTest extends BaseUnitTest {
    @Test
    void dailyAndWeeklyCadenceKeepTheirDeclaredIntervals() {
        assertThat(ReviewCadence.DAILY.interval()).isEqualTo(Duration.ofDays(1));
        assertThat(ReviewCadence.WEEKLY.interval()).isEqualTo(Duration.ofDays(7));
        assertThat(ReviewCadence.DAILY.maxLookback()).isEqualTo(2);
        assertThat(ReviewCadence.WEEKLY.maxLookback()).isEqualTo(7);
    }

    @Test
    void repositoryScheduleStoresBudgetWithoutALookback() {
        var s = ReviewSchedule.builder()
                .id(UUID.randomUUID())
                .workspaceId(1)
                .kind("scm.repository")
                .budgetUsd(new BigDecimal("4.2500"))
                .build();
        assertThat(s.getLookbackDays()).isNull();
        assertThat(s.getBudgetUsd()).isEqualByComparingTo("4.2500");
    }

    @Test
    void enabledHasAReverseState() {
        var s = ReviewSchedule.builder().kind("scm.pull_request").build();
        assertThat(s.isEnabled()).isTrue();
        s.setEnabled(false);
        assertThat(s.isEnabled()).isFalse();
        s.setEnabled(true);
        assertThat(s.isEnabled()).isTrue();
    }
}
