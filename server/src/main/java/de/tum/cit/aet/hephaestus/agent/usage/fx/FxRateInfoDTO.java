package de.tum.cit.aet.hephaestus.agent.usage.fx;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.jspecify.annotations.NonNull;

/**
 * The rate a client should use to render a USD figure in the instance's display currency, plus the
 * date that rate was published on.
 *
 * <p>Display estimate only — never an input to a budget, a price or the ledger. Every amount in the
 * response it rides along with stays USD; this record is what lets a UI show "≈ €12.40 (rate of
 * 2026-07-24)" beside it without inventing a rate of its own.
 *
 * <p><b>Direction.</b> The ECB publishes USD per 1 EUR ({@code 1.1377}); clients need the opposite,
 * EUR per 1 USD. {@link #fromEcbRate} is the single place that inversion happens — a second
 * inversion somewhere downstream is the classic FX bug, and having exactly one owner of the
 * direction is what makes it impossible here.
 */
@Schema(
    description = "Display-only currency conversion for the USD amounts in this response. Multiply a USD " +
        "amount by ratePerUsd to get the display-currency estimate; always label it as an estimate and " +
        "show rateDate, which is the date the rate was actually published on (not necessarily today)."
)
public record FxRateInfoDTO(
    @NonNull @Schema(description = "ISO 4217 code of the display currency", example = "EUR") String currencyCode,
    @NonNull
    @Schema(description = "Units of the display currency per 1 USD, at 6 decimal places", example = "0.878966")
    BigDecimal ratePerUsd,
    @NonNull
    @Schema(
        description = "The date the underlying reference rate was published. A closed month always reports " +
            "a date inside that month, so its converted figure never changes once the month ends.",
        example = "2026-07-24"
    )
    LocalDate rateDate
) {
    /** Decimal places the inverted rate is reported at — enough that a per-cent figure never truncates. */
    static final int RATE_SCALE = 6;

    /**
     * Invert an ECB quote (USD per 1 EUR) into the direction clients multiply by (display currency per
     * 1 USD). The ONLY inversion in the codebase.
     *
     * @param currencyCode ISO 4217 code of the display currency
     * @param usdPerDisplayUnit the published rate, US dollars per one unit of the display currency
     * @param rateDate the publication date of that rate
     */
    public static FxRateInfoDTO fromEcbRate(String currencyCode, BigDecimal usdPerDisplayUnit, LocalDate rateDate) {
        return new FxRateInfoDTO(
            currencyCode,
            BigDecimal.ONE.divide(usdPerDisplayUnit, RATE_SCALE, RoundingMode.HALF_UP),
            rateDate
        );
    }
}
