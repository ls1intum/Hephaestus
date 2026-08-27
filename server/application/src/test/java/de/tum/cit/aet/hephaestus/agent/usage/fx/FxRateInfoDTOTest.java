package de.tum.cit.aet.hephaestus.agent.usage.fx;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The one place the ECB's direction is inverted. A second inversion downstream — or a forgotten one —
 * is the classic FX bug, so the direction is pinned with a rate whose two readings are far enough apart
 * (1.1377 vs 0.878966) that a flip could never pass unnoticed. The second row is a weak display
 * currency, where fewer decimal places would round the per-USD rate to zero.
 */
class FxRateInfoDTOTest extends BaseUnitTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 24);

    @ParameterizedTest(name = "1 / {0} = {1}")
    @CsvSource({"1.1377, 0.878966", "18542.290000, 0.000054"})
    void shouldInvertEcbQuoteToSixDecimalPlaces(String usdPerEur, String expectedRatePerUsd) {
        FxRateInfoDTO info = FxRateInfoDTO.fromEcbRate("EUR", new BigDecimal(usdPerEur), DATE);

        assertThat(info.ratePerUsd()).isEqualByComparingTo(expectedRatePerUsd);
        assertThat(info.ratePerUsd().scale()).isEqualTo(6);
    }

    @Test
    void shouldStampEcbAsTheSourceSoTheUiCanNameThePublisher() {
        FxRateInfoDTO info = FxRateInfoDTO.fromEcbRate("EUR", new BigDecimal("1.1377"), DATE);

        assertThat(info.source()).isEqualTo("ECB");
    }
}
