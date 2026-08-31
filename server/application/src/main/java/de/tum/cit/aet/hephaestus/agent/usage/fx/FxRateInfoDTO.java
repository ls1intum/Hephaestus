package de.tum.cit.aet.hephaestus.agent.usage.fx;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.jspecify.annotations.NonNull;

/**
 * Display estimate only — never an input to a budget, a price or the ledger.
 *
 * <p>The ECB publishes USD per 1 EUR; clients need the opposite. {@code fromEcbRate} is the single
 * place that inversion happens.
 */
@Schema(
        description = "Display-only currency conversion for the USD amounts in this response. Multiply a USD "
                + "amount by ratePerUsd to get the display-currency estimate; always label it as an estimate and "
                + "show rateDate, which is the date the rate was actually published on (not necessarily today), and "
                + "attribute it to source.")
public record FxRateInfoDTO(
        @NonNull @Schema(description = "ISO 4217 code of the display currency", example = "EUR")
        String currencyCode,

        @NonNull
        @Schema(description = "Units of the display currency per 1 USD, at 6 decimal places", example = "0.878966")
        BigDecimal ratePerUsd,

        @NonNull
        @Schema(
                description = "The date the underlying reference rate was published. A closed month always reports "
                        + "a date inside that month, so its converted figure never changes once the month ends.",
                example = "2026-07-24")
        LocalDate rateDate,

        @NonNull
        @Schema(
                description = "Who published the rate, so a disclosure can name it instead of saying \"a reference "
                        + "rate\". ECB = the European Central Bank's daily euro foreign-exchange reference rates.",
                allowableValues = {FxRateInfoDTO.ECB_SOURCE},
                example = FxRateInfoDTO.ECB_SOURCE)
        String source) {
    /**
     * The only publisher this codebase can report: {@code hephaestus.llm.fx.daily-url} is overridable
     * only so an air-gapped instance can fetch a mirror of the same ECB document, and
     * {@link EcbFxRateClient} returns empty for anything that is not one.
     */
    public static final String ECB_SOURCE = "ECB";

    /** Decimal places the inverted rate is reported at — enough that a per-cent figure never truncates. */
    static final int RATE_SCALE = 6;

    /**
     * Inverts an ECB quote (USD per 1 EUR) into the direction clients multiply by (display currency per
     * 1 USD). The only inversion in the codebase, and the only place {@code ECB_SOURCE} is stamped.
     */
    public static FxRateInfoDTO fromEcbRate(String currencyCode, BigDecimal usdPerDisplayUnit, LocalDate rateDate) {
        return new FxRateInfoDTO(
                currencyCode,
                BigDecimal.ONE.divide(usdPerDisplayUnit, RATE_SCALE, RoundingMode.HALF_UP),
                rateDate,
                ECB_SOURCE);
    }
}
