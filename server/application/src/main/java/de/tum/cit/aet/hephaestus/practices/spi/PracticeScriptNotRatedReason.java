package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.Locale;

/**
 * Why a practice script could not have a model rate a place. Never "nothing found". The runner's
 * {@code REASONS} in {@code docker/agents/practice-scripts/lib/contract.ts} holds the same values in their
 * {@link #wire()} form.
 */
public enum PracticeScriptNotRatedReason {
    UNAVAILABLE,
    BUDGET,
    DEADLINE,
    TOO_LARGE,
    OFF_FORMAT,
    REFUSED,
    ERROR;

    /** The runner's spelling: lower case, words joined by a hyphen. */
    public String wire() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
