package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Duration;

public enum ReviewCadence {
    DAILY,
    WEEKLY;

    public Duration interval() {
        return Duration.ofDays(this == DAILY ? 1 : 7);
    }

    public int maxLookback() {
        return this == DAILY ? 2 : 7;
    }
}
