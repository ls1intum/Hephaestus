package de.tum.cit.aet.hephaestus.practices.review.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ReviewBatchServiceTest extends BaseUnitTest {
    @Test
    void updatesRequireIfMatch() {
        assertThatThrownBy(() -> ReviewBatchService.checkVersion(0, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(
                                ((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(428));
    }

    @Test
    void staleVersionCannotOverwriteBatch() {
        assertThatThrownBy(() -> ReviewBatchService.checkVersion(2, EntityTagPrecondition.parse("\"1\"")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(
                                ((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(412));
    }

    @Test
    void exactVersionCanChangeState() {
        assertThatCode(() -> ReviewBatchService.checkVersion(2, EntityTagPrecondition.parse("\"2\"")))
                .doesNotThrowAnyException();
    }

    @Test
    void decimalCostsRemainStrings() {
        assertThat(ReviewBatchService.decimal(new BigDecimal("1.250000"))).isEqualTo("1.250000");
        assertThat(ReviewBatchService.decimal(null)).isNull();
    }

    @Test
    void invalidLimitsAreRejected() {
        assertThatThrownBy(() -> new ReviewBatchProperties(0, Duration.ofDays(400), 100, Duration.ofDays(90)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReviewBatchProperties(1, Duration.ofDays(400), 0, Duration.ofDays(90)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
