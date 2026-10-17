package de.tum.cit.aet.hephaestus.practices.spi;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ModelSlot {
    CHAT("chat"),
    DECISION("decision"),
    EMBEDDING("embedding"),
    RERANKING("reranking");

    private final String wireValue;

    ModelSlot(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
