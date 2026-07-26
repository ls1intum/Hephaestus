package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelPrice;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import jakarta.persistence.Column;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

/**
 * Pins the claim the API description makes about money, against the values production actually
 * declares.
 *
 * <p>Money leaves this server as an exact decimal ({@code BigDecimal}, {@code NUMERIC} column) and
 * lands in the browser as a binary64 {@code number}, because JavaScript has no decimal type. That is
 * only honest if the conversion is lossless for every value we can produce, so each test below takes
 * a bound from the code that enforces it — the ledger's cost clamp, the cap request validator, the
 * mapped column scales — and puts that value through the trip.
 *
 * <p>Binary64 round-trips any decimal of at most 15 significant digits exactly ({@code DBL_DIG}).
 * Our amounts are quantised, so significant digits are just "integer digits + scale". Asserting the
 * property on invented literals would prove something about binary64 rather than about this system;
 * the point is that the values we can actually emit sit inside it, with the distance measured.
 *
 * <p>What none of this licenses is client-side arithmetic. A single value survives the trip; a sum of
 * thirty-one of them accumulates error the server never had. Totals are computed here and shipped as
 * fields for exactly that reason.
 */
class MoneyWirePrecisionTest extends BaseUnitTest {

    /** Binary64's guaranteed decimal round-trip width. */
    private static final int SIGNIFICANT_DIGITS = 15;

    /**
     * The ledger's own bounds, taken from the clamps rather than from a literal: whatever
     * {@link LlmPriceSnapshot#calculateCost} is willing to store has to survive the wire, and these
     * two calls are the only ways it produces a value it did not compute.
     *
     * <p>Kills widening {@code MAX_COST} to the column's real maximum: {@code 999999999999.999999}
     * is eighteen significant digits and comes back from the browser as a different number, so the
     * ledger would hold amounts the API cannot state.
     */
    @Test
    void everyCostTheLedgerWillStoreSurvivesTheWire() {
        BigDecimal largest = clampedCost(new BigDecimal("1000000000"), 1_000_000_000L);
        BigDecimal smallest = clampedCost(new BigDecimal("0.00000001"), 1L);

        assertThat(largest).as("the maximum clamp must be reached by this input").isGreaterThan(BigDecimal.ONE);
        assertThat(smallest).as("the minimum clamp must be reached by this input").isLessThan(BigDecimal.ONE);
        assertRoundTrips(largest);
        assertRoundTrips(smallest);

        // The cliff is real and measured, not assumed: one integer digit past the ceiling is where the
        // shortest decimal that reproduces the double stops being the decimal we sent.
        BigDecimal oneDigitTooWide = largestAt(largest.precision() + 1, largest.scale());
        assertThat(asBrowserWouldSee(oneDigitTooWide)).isNotEqualByComparingTo(oneDigitTooWide);
    }

    /**
     * The cap is the one money field a human types, so it has two guards that must agree: the request
     * validator that decides what is accepted, and the column that has to hold it. A validator wider
     * than its column turns a typo into a database error instead of a 400; a validator wider than
     * fifteen digits turns an accepted cap into a number the browser renders differently.
     *
     * <p>Kills {@code @Digits(integer = 9)} on the request (past the column) and
     * {@code precision = 9} on the column (under the request).
     */
    @Test
    void theBudgetCapFitsBothItsValidatorAndItsColumn() throws NoSuchMethodException, NoSuchFieldException {
        Digits accepted = UpdateLlmBudgetRequestDTO.class.getMethod("monthlyBudgetUsd").getAnnotation(Digits.class);
        Column stored = Workspace.class.getDeclaredField("monthlyLlmBudgetUsd").getAnnotation(Column.class);

        assertThat(accepted).as("the cap's width must stay declared, or nothing bounds it").isNotNull();
        assertThat(accepted.integer() + accepted.fraction()).isLessThanOrEqualTo(SIGNIFICANT_DIGITS);
        assertThat(accepted.fraction()).isLessThanOrEqualTo(stored.scale());
        assertThat(accepted.integer() + accepted.fraction()).isLessThanOrEqualTo(stored.precision());

        assertRoundTrips(largestAt(accepted.integer() + accepted.fraction(), accepted.fraction()));
        assertRoundTrips(largestAt(stored.precision(), stored.scale()));
    }

    /**
     * A frozen rate is copied from the catalog onto every ledger row it prices, so the two columns
     * are one number in two places: a narrower ledger column would silently truncate the rate an
     * event was actually billed at, and the provenance the ledger exists to keep would be a rounded
     * version of the price the admin set.
     *
     * <p>Kills changing either column's {@code scale} without the other. Also pins that a rate
     * carrying all of those decimal places is still exact on the wire across the range a catalog
     * states — the tighter of the two cases, since each decimal place costs an integer digit.
     */
    @Test
    void aFrozenRateIsTheSameNumberInTheCatalogAndTheLedger() throws NoSuchFieldException {
        Column catalog = LlmModelPrice.class.getDeclaredField("per1mInputUsd").getAnnotation(Column.class);
        Column ledger = LlmUsageEvent.class.getDeclaredField("appliedPer1mInputUsd").getAnnotation(Column.class);

        assertThat(ledger.scale()).isEqualTo(catalog.scale());
        assertThat(ledger.precision()).isGreaterThanOrEqualTo(catalog.precision());

        assertRoundTrips(largestAt(SIGNIFICANT_DIGITS, catalog.scale())); // 9999999.99999999
        assertRoundTrips(BigDecimal.ONE.movePointLeft(catalog.scale())); // the smallest rate settable
        assertRoundTrips(new BigDecimal("75.00000001")); // a frontier model's output rate, plus one unit
    }

    /** The amount {@link LlmPriceSnapshot#calculateCost} substitutes for what {@code rate} really produced. */
    private static BigDecimal clampedCost(BigDecimal per1mInputUsd, long inputTokens) {
        LlmPriceSnapshot price = new LlmPriceSnapshot(
            FundingSource.INSTANCE,
            PricingState.PRICED,
            1L,
            null,
            per1mInputUsd,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );
        LlmPriceSnapshot.Cost cost = price.calculateCost(inputTokens, 0, 0, 0);
        assertThat(cost.clamp()).as("this input must reach a clamp, or the test is asserting nothing").isNotNull();
        return cost.usd();
    }

    /** The largest value {@code NUMERIC(precision, scale)} holds, e.g. {@code 99999999.99}. */
    private static BigDecimal largestAt(int precision, int scale) {
        return BigDecimal.ONE.movePointRight(precision - scale)
            .subtract(BigDecimal.ONE.movePointLeft(scale))
            .setScale(scale, RoundingMode.UNNECESSARY);
    }

    private static void assertRoundTrips(BigDecimal amount) {
        assertThat(asBrowserWouldSee(amount))
            .as("%s must survive JSON number -> binary64 -> shortest decimal", amount.toPlainString())
            .isEqualByComparingTo(amount);
    }

    /**
     * The trip a money field actually makes: the text Jackson emits for the number, parsed into a
     * binary64, then rendered back the way a JS runtime renders one. {@code Double.toString} has produced
     * the shortest round-tripping decimal since JDK 19 (JDK-4511638), which is the same rule
     * {@code Number.prototype.toString} follows — so this is the browser's view, not an approximation of
     * it.
     *
     * <p>It also covers the E-notation {@code BigDecimal#toString} switches to once the adjusted
     * exponent drops below -6, which is how a scale-8 rate of {@code 0.00000001} goes out as
     * {@code 1E-8}: a well-formed JSON number that {@code JSON.parse} reads as {@code 1e-8}.
     */
    private static BigDecimal asBrowserWouldSee(BigDecimal amount) {
        String onTheWire = amount.toString();
        return new BigDecimal(Double.toString(Double.parseDouble(onTheWire)));
    }
}
