package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.Locale;

/** Why a practice program stopped without a candidate; absent exactly when its run is {@code OK}. */
public enum PracticeScriptStopReason {
    UNAVAILABLE,
    BUDGET,
    DEADLINE,
    TOO_LARGE,
    OFF_FORMAT,
    REFUSED,
    ERROR,
    CANCELLED,
    /** The program is not one the runner can run, such as a script of the retired leads contract. */
    INVALID_PROGRAM,
    /** The program returned a value that is not a candidate. */
    INVALID_CANDIDATE;

    /** The runner's spelling: lower case, words joined by a hyphen. */
    public String wire() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
