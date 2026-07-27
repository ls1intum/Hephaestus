package de.tum.cit.aet.hephaestus.agent.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The per-run timeout is a bounded product limit at BOTH ends, and the ceiling is the load-bearing one.
 *
 * <p>Anything that has to outlive a run is sized from {@link AgentBindingLimits#MAX_TIMEOUT_SECONDS} —
 * {@code MentorInFlightReaper} treats a turn older than its window as abandoned, bills it, and closes
 * the conversation. With no ceiling that sweep has no safe window it could pick: whatever constant it
 * chose, an administrator could configure a binding past it and have live turns reaped mid-answer.
 * Delete the {@code @Max} and this test fails; the reaper's own test then fails too, because its window
 * no longer clears anything.
 *
 * <p>The clamp in {@code MentorPiAdapter} covers rows that never came through this API. This covers the
 * rows that do — refusing the value outright, with a 400 naming the field, instead of silently running
 * something other than what the administrator asked for.
 */
class AgentBindingTimeoutRangeTest extends BaseUnitTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        factory.close();
    }

    @Test
    @DisplayName("both ends of the configurable range are accepted")
    void acceptsTheWholeRange() {
        assertThat(violationsFor(AgentBindingLimits.MIN_TIMEOUT_SECONDS)).isEmpty();
        assertThat(violationsFor(AgentBindingLimits.MAX_TIMEOUT_SECONDS)).isEmpty();
    }

    @Test
    @DisplayName("a timeout past either end is refused before any binding is written")
    void refusesOutsideTheRange() {
        assertThat(violationsFor(AgentBindingLimits.MIN_TIMEOUT_SECONDS - 1))
            .as("below the floor a run cannot finish a single model call")
            .isNotEmpty();
        assertThat(violationsFor(AgentBindingLimits.MAX_TIMEOUT_SECONDS + 1))
            .as("above the ceiling a turn can outlive the sweep that bills abandoned turns")
            .isNotEmpty();
    }

    @Test
    @DisplayName("omitting the timeout keeps the binding's current value rather than failing validation")
    void acceptsAnAbsentTimeout() {
        assertThat(violationsFor(null)).isEmpty();
    }

    private static Set<ConstraintViolation<AgentBindingRequestDTO>> violationsFor(Integer timeoutSeconds) {
        AgentBindingRequestDTO request = new AgentBindingRequestDTO(1L, null, timeoutSeconds, null, null, null);
        return validator
            .validate(request)
            .stream()
            .filter(v -> v.getPropertyPath().toString().equals("timeoutSeconds"))
            .collect(Collectors.toSet());
    }
}
