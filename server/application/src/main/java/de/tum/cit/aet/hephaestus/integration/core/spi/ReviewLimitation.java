package de.tum.cit.aet.hephaestus.integration.core.spi;

public record ReviewLimitation(String code, String description) {
    public ReviewLimitation {

        if (code.isBlank() || description.isBlank()) throw new IllegalArgumentException("Invalid review limitation");
    }
}
