package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.Objects;

/**
 * A claim that evidence about an artifact kind can never support, however completely it is captured.
 *
 * <p>Stated per kind by {@link ArtifactDescriptor} rather than as a switch in the practices module, so a
 * new kind states its own limit instead of finding a switch that throws.
 *
 * @param code        a stable, queryable identifier, {@code SCREAMING_SNAKE_CASE}
 * @param description one sentence a person reads, saying what the evidence does not establish
 */
public record ReviewLimitation(String code, String description) {
    public ReviewLimitation {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(description, "description");
        if (code.isBlank()) {
            throw new IllegalArgumentException("review limitation code must not be blank");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("review limitation description must not be blank: " + code);
        }
    }
}
