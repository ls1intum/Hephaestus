package de.tum.cit.aet.hephaestus.agent.usage.fx;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.jspecify.annotations.NonNull;

/**
 * Display estimate only — never an input to a budget, a price or the ledger; every amount in the
 * response it rides along with stays USD.
 *
 * <p><b>Direction.</b> The ECB publishes USD per 1 EUR ({@code 1.1377}); clients need the opposite,
 * EUR per 1 USD. {@link #fromEcbRate} is the single place that inversion happens — a second inversion
 * downstream is the classic FX bug.
 */
@Schema(
    description = "Display-only currency conversion for the USD amounts in this response. Multiply a USD " +
        "amount by ratePerUsd to get the display-currency estimate; always label it as an estimate and " +
        "show rateDate, which is the date the rate was actually published on (not necessarily today), and " +
        "attribute it to source."
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
    LocalDate rateDate,
    @NonNull
    @Schema(
        description = "Who published the rate, so a disclosure can name it instead of saying \"a reference " +
            "rate\". ECB = the European Central Bank's daily euro foreign-exchange reference rates.",
        allowableValues = { FxRateInfoDTO.ECB_SOURCE },
        example = FxRateInfoDTO.ECB_SOURCE
    )
    String source
) {
    /**
     * The only publisher this codebase can currently report.
     *
     * <p>It is a fact about the payload, not a guess the UI makes, and it survives the one knob that
     * looks like it could falsify it: {@code hephaestus.llm.fx.daily-url} is overridable, but only so an
     * air-gapped instance can fetch a MIRROR of the same document. {@code EcbFxRateClient} parses the
     * ECB's {@code Cube}/{@code time}/{@code currency} grammar and reads a quote per 1 EUR, and
     * {@link #fromEcbRate} inverts on that assumption — point it at any other feed and it returns empty
     * rather than a rate from somewhere else. So the URL changes where the copy is fetched from, never
     * who published it.
     */
    public static final String ECB_SOURCE = "ECB";

    /** Decimal places the inverted rate is reported at — enough that a per-cent figure never truncates. */
    static final int RATE_SCALE = 6;

    /**
     * Inverts an ECB quote (USD per 1 EUR) into the direction clients multiply by (display currency per
     * 1 USD). The ONLY inversion in the codebase.
     *
     * <p>It is also the only place {@link #ECB_SOURCE} is stamped: the factory that knows the quote is an
     * ECB one is the factory that says so, so a second provider would arrive as a second factory rather
     * than as a caller passing the wrong string.
     */
    public static FxRateInfoDTO fromEcbRate(String currencyCode, BigDecimal usdPerDisplayUnit, LocalDate rateDate) {
        return new FxRateInfoDTO(
            currencyCode,
            BigDecimal.ONE.divide(usdPerDisplayUnit, RATE_SCALE, RoundingMode.HALF_UP),
            rateDate,
            ECB_SOURCE
        );
    }
}
