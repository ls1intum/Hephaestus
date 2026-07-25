package de.tum.cit.aet.hephaestus.agent.usage.fx;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one place the ECB's direction is inverted. A second inversion downstream — or a forgotten one
 * — is the classic FX bug, so the direction is pinned here with a rate whose two readings are far
 * enough apart (1.1377 vs 0.878966) that a flip could never pass unnoticed.
 */
class FxRateInfoDTOTest extends BaseUnitTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 24);

    @Test
    @DisplayName("should invert the ECB quote to display-currency-per-USD at 6 decimal places")
    void shouldInvertEcbQuoteToSixDecimalPlacesWhenBuildingInfo() {
        FxRateInfoDTO info = FxRateInfoDTO.fromEcbRate("EUR", new BigDecimal("1.1377"), DATE);

        // 1 / 1.1377 = 0.8789663…, half-up at 6 dp.
        assertThat(info.ratePerUsd()).isEqualByComparingTo("0.878966");
        assertThat(info.ratePerUsd().scale()).isEqualTo(6);
    }

    @Test
    @DisplayName("should carry the inverted direction, not the published one")
    void shouldCarryInvertedDirectionWhenBuildingInfo() {
        BigDecimal usdPerEur = new BigDecimal("1.1377");

        FxRateInfoDTO info = FxRateInfoDTO.fromEcbRate("EUR", usdPerEur, DATE);

        // Below 1 because a euro is worth more than a dollar — the published quote is above 1.
        assertThat(info.ratePerUsd()).isLessThan(BigDecimal.ONE);
        assertThat(info.ratePerUsd()).isNotEqualByComparingTo(usdPerEur);
        // Round-tripping lands back on the published rate, so the two readings are genuinely reciprocal.
        assertThat(BigDecimal.ONE.divide(info.ratePerUsd(), 4, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo(
            usdPerEur
        );
    }

    @Test
    @DisplayName("should apply the rate so that a USD amount converts to a smaller EUR amount")
    void shouldConvertUsdAmountToSmallerDisplayAmountWhenEuroIsStronger() {
        FxRateInfoDTO info = FxRateInfoDTO.fromEcbRate("EUR", new BigDecimal("1.1377"), DATE);

        BigDecimal eur = new BigDecimal("100.00").multiply(info.ratePerUsd());

        assertThat(eur).isEqualByComparingTo("87.8966");
    }

    @Test
    @DisplayName("should report the currency code and rate date it was given")
    void shouldReportGivenCurrencyAndDateWhenBuildingInfo() {
        FxRateInfoDTO info = FxRateInfoDTO.fromEcbRate("EUR", new BigDecimal("1.0500"), DATE);

        assertThat(info.currencyCode()).isEqualTo("EUR");
        assertThat(info.rateDate()).isEqualTo(DATE);
    }

    @Test
    @DisplayName("should not truncate a rate that needs all six decimal places")
    void shouldKeepSixDecimalPlacesWhenRateIsLarge() {
        // A weak display currency yields a small per-USD rate; truncating early would round it to zero.
        FxRateInfoDTO info = FxRateInfoDTO.fromEcbRate("EUR", new BigDecimal("18542.290000"), DATE);

        assertThat(info.ratePerUsd()).isEqualByComparingTo("0.000054");
    }
}
