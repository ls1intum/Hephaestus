package de.tum.cit.aet.hephaestus.practices.research;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ResearchDisclosureServiceTest extends BaseUnitTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Test
    void shouldAcceptAnyFutureUseUntilTheStudyProtocolNames() {
        assertThatCode(() -> ResearchDisclosureService.requireFutureUse(NOW.plusSeconds(1), NOW))
                .doesNotThrowAnyException();
        assertThatCode(() -> ResearchDisclosureService.requireFutureUse(NOW.plus(3650, ChronoUnit.DAYS), NOW))
                .as("no invented maximum")
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRefuseAUseUntilThatIsNotInTheFuture() {
        assertThatThrownBy(() -> ResearchDisclosureService.requireFutureUse(NOW, NOW))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ResearchDisclosureService.requireFutureUse(NOW.minusSeconds(1), NOW))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void shouldBoundOnlyTheReadWindow() {
        LocalDate first = LocalDate.parse("2026-01-01");
        assertThatCode(() -> ResearchDisclosureService.requireWindow(first, first))
                .doesNotThrowAnyException();
        assertThatCode(() -> ResearchDisclosureService.requireWindow(first, first.plusDays(399)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ResearchDisclosureService.requireWindow(first, first.plusDays(400)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ResearchDisclosureService.requireWindow(first, first.minusDays(1)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
