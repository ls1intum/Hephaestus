package de.tum.cit.aet.hephaestus.agent;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The display currency is validated against the set the instance can actually convert to, not against
 * the shape of a currency code: {@code GBP} is well formed, and an instance that accepted it could only
 * ever have shown USD — a missing feature the operator would go looking for in the wrong place.
 */
class LlmPropertiesTest extends BaseUnitTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void openValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static Set<ConstraintViolation<LlmProperties>> validate(String displayCurrency) {
        return validator.validate(new LlmProperties(
                displayCurrency, new LlmProperties.Egress(false), new LlmProperties.Fx(LlmProperties.ECB_DAILY_URL)));
    }

    @ParameterizedTest(name = "display-currency={0} boots")
    @CsvSource({"'', unset — the feature stays off", "EUR, the one supported currency", "eur, case is irrelevant"})
    void acceptsOnlyEmptyOrASupportedCurrency(String configured, String why) {
        assertThat(validate(configured)).as(why).isEmpty();
    }

    @ParameterizedTest(name = "display-currency={0} fails startup")
    @ValueSource(strings = {"GBP", "CHF", "USD", "EURO", "€", "eu"})
    void refusesToBootOnAnythingItCannotConvertTo(String configured) {
        Set<ConstraintViolation<LlmProperties>> violations = validate(configured);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains(LlmProperties.SUPPORTED_DISPLAY_CURRENCIES);
    }
}
