package de.tum.cit.aet.hephaestus.practices.spi;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Need {
    REQUIRED("required"),
    OPTIONAL("optional");

    private final String wireValue;

    Need(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
