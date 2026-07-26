package de.tum.cit.aet.hephaestus.agent.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The accepted WIDTH of a per-1M-token rate, which is narrower than its column and has to be.
 *
 * <p>{@code llm_model_price} is {@code NUMERIC(18,8)} — eighteen significant digits. A binary64
 * carries fifteen, and every money field this API returns reaches the browser as a JS {@code number}.
 * A rate between those two bounds is therefore stored as one value and quoted back to the admin who
 * typed it as a different one: {@code 9999999999.99999999} comes back as {@code 10000000000}. The
 * admin's own screen would disagree with the price the ledger bills at.
 *
 * <p>{@code MoneyWirePrecisionTest} measures where the cliff is; this asserts the input can't reach it.
 * Two independent guards, deliberately: {@code @Digits} rejects the request before any service runs and
 * names the offending field, while {@link LlmPriceValidation} holds the same line for all four entry
 * points that can set a rate — instance create and reprice, workspace BYO create and update — so the
 * bound cannot be widened by adding a fifth DTO that forgets the annotation.
 */
class LlmPriceWidthTest extends BaseUnitTest {

    /** {@code MoneyWirePrecisionTest.shouldRoundTripEveryPer1mRateBelowTenMillionDollars}'s ceiling. */
    private static final BigDecimal WIDEST_SAFE_RATE = new BigDecimal("9999999.99999999");

    /** One unit past it — still legal in {@code NUMERIC(18,8)}, no longer exact as a double. */
    private static final BigDecimal FIRST_UNSAFE_RATE = new BigDecimal("10000000.00000000");

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void stopValidator() {
        factory.close();
    }

    @Test
    @DisplayName("the widest rate that survives the wire is accepted")
    void shouldAcceptTheWidestSafeRate() {
        Set<ConstraintViolation<UpdateLlmModelPriceRequestDTO>> violations = validator.validate(
            priceRequest(WIDEST_SAFE_RATE)
        );

        assertThat(violations).as("the bound must be usable, not merely safe").isEmpty();
        assertThatCode(() -> validate(WIDEST_SAFE_RATE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a rate the browser would round is refused by the request validator")
    void shouldRejectARateWiderThanBinary64() {
        Set<ConstraintViolation<UpdateLlmModelPriceRequestDTO>> violations = validator.validate(
            priceRequest(FIRST_UNSAFE_RATE)
        );

        // Drop @Digits from per1mInputUsd and this is empty: the rate is stored, then displayed as a
        // different number.
        assertThat(violations).as("@Digits is what stops a rate the API cannot quote back accurately").isNotEmpty();
        assertThat(violations).allSatisfy(violation ->
            assertThat(violation.getPropertyPath().toString()).isEqualTo("per1mInputUsd")
        );
    }

    @Test
    @DisplayName("the shared validator holds the same bound for every entry point")
    void shouldRejectAnOversizedRateInTheSharedValidator() {
        // The DTO annotation only guards ITS endpoint. Workspace BYO models reach the same rates through
        // their own request records, so the magnitude check has to live where all four paths meet.
        assertThatThrownBy(() -> validate(FIRST_UNSAFE_RATE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("10000000");
    }

    @Test
    @DisplayName("an oversized cache rate is caught too, not just the required two")
    void shouldRejectAnOversizedOptionalRate() {
        assertThatThrownBy(() ->
            LlmPriceValidation.validate(
                PricingMode.PRICED,
                new BigDecimal("1.00"),
                new BigDecimal("2.00"),
                FIRST_UNSAFE_RATE,
                null,
                null
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    private static void validate(BigDecimal inputRate) {
        LlmPriceValidation.validate(PricingMode.PRICED, inputRate, new BigDecimal("2.00"), null, null, null);
    }

    private static UpdateLlmModelPriceRequestDTO priceRequest(BigDecimal inputRate) {
        return new UpdateLlmModelPriceRequestDTO(
            PricingMode.PRICED,
            inputRate,
            new BigDecimal("2.00"),
            null,
            null,
            null
        );
    }
}
