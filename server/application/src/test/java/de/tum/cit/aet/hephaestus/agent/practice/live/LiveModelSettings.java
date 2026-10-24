package de.tum.cit.aet.hephaestus.agent.practice.live;

import de.tum.cit.aet.hephaestus.agent.catalog.ReasoningEffort;
import java.util.Arrays;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The model settings a live practice run sends. Each optional system property replaces one harness default, so the
 * same cases can be measured with reasoning or a larger output allowance. Without them a run is unchanged.
 */
record LiveModelSettings(
        int contextWindow, int maxOutputTokens, @Nullable ReasoningEffort reasoningEffort) {

    static final String CONTEXT_WINDOW_PROPERTY = "hephaestus.live.criteriaContextWindow";
    static final String MAX_OUTPUT_TOKENS_PROPERTY = "hephaestus.live.criteriaMaxOutputTokens";
    static final String REASONING_EFFORT_PROPERTY = "hephaestus.live.criteriaReasoningEffort";

    static final LiveModelSettings DEFAULTS = new LiveModelSettings(131_072, 4096, null);

    static LiveModelSettings fromSystemProperties() {
        return from(System::getProperty);
    }

    /** The settings that {@code property} names; an invalid value fails before any model request. */
    static LiveModelSettings from(Function<String, @Nullable String> property) {
        return new LiveModelSettings(
                positive(property, CONTEXT_WINDOW_PROPERTY, DEFAULTS.contextWindow()),
                positive(property, MAX_OUTPUT_TOKENS_PROPERTY, DEFAULTS.maxOutputTokens()),
                reasoningEffort(property.apply(REASONING_EFFORT_PROPERTY)));
    }

    private static int positive(Function<String, @Nullable String> property, String name, int fallback) {
        String value = property.apply(name);
        if (value == null) {
            return fallback;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a positive integer, not '" + value + "'", e);
        }
        if (parsed < 1) {
            throw new IllegalArgumentException(name + " must be a positive integer, not '" + value + "'");
        }
        return parsed;
    }

    private static @Nullable ReasoningEffort reasoningEffort(@Nullable String value) {
        if (value == null) {
            return DEFAULTS.reasoningEffort();
        }
        try {
            return ReasoningEffort.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    REASONING_EFFORT_PROPERTY + " must be one of " + Arrays.toString(ReasoningEffort.values())
                            + ", not '" + value + "'",
                    e);
        }
    }
}
