package de.tum.cit.aet.hephaestus.practices.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ScanBudgetInputTest extends BaseUnitTest {
    @Test
    void acceptsOnlyBoundedPlainDecimals() {
        assertThat(ScanQueryService.parseBudget("0.0001")).isEqualByComparingTo("0.0001");
        assertThat(ScanQueryService.parseBudget("100000")).isEqualByComparingTo("100000");
        for (var invalid : List.of("0", "-1", "100001", "0.00001", "1e2", "NaN", "Infinity", "01.0"))
            assertThatThrownBy(() -> ScanQueryService.parseBudget(invalid)).isInstanceOf(ResponseStatusException.class);
    }
}
