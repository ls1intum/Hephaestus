package de.tum.cit.aet.hephaestus.practices;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public record ReviewLimit(
        @JsonProperty(required = true) String code,
        @JsonProperty(required = true) String description) implements ClosedPracticeInput {
    public ReviewLimit {
        Objects.requireNonNull(code);
        Objects.requireNonNull(description);
        if (code.isBlank() || code.length() > 64 || description.isBlank() || description.length() > 2000) {
            throw new IllegalArgumentException("Give the review limit a code and a description.");
        }
    }
}
