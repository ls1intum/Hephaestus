package de.tum.cit.aet.hephaestus.practices.scan;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.scan.dto.StartScanRequestDTO;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScanBudgetInputTest extends BaseUnitTest {
    @Test
    void shouldValidateBoundedPlainDecimalBudgetsAtTheRequestBoundary() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (var valid : List.of("0.0001", "100000"))
                assertThat(validator.validate(new StartScanRequestDTO("1", null, null, null, valid, true)))
                        .isEmpty();
            for (var invalid : List.of("0", "-1", "100001", "0.00001", "1e2", "NaN", "Infinity", "01.0"))
                assertThat(validator.validate(new StartScanRequestDTO("1", null, null, null, invalid, true)))
                        .anyMatch(v -> v.getPropertyPath().toString().equals("budgetUsd"));
        }
    }
}
