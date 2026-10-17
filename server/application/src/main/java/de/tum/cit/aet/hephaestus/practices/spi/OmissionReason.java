package de.tum.cit.aet.hephaestus.practices.spi;

import com.fasterxml.jackson.annotation.JsonValue;

public enum OmissionReason {
    SELECTION("selection"),
    TRUNCATED("truncated"),
    UNAVAILABLE("unavailable"),
    BUDGET("budget"),
    DEADLINE("deadline"),
    TOO_LARGE("too-large"),
    OFF_FORMAT("off-format"),
    REFUSED("refused"),
    ERROR("error"),
    CANCELLED("cancelled");

    private final String wireValue;

    OmissionReason(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
