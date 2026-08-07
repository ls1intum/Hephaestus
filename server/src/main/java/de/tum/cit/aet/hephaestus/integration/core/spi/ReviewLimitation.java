package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.Objects;

/**
 * A claim that evidence about an artifact kind can never support, however completely it is captured.
 *
 * <p>Stated by the kind's {@link ArtifactDescriptor} because it is a fact about the domain, not about
 * any one practice: no amount of repository evidence establishes what a deployed service did at
 * runtime, and no amount of document evidence establishes whether the thing the document describes was
 * ever built. Practices used to carry these as a per-kind switch inside the practices module — the last
 * place a new artifact kind still forced an edit there — and a fifth kind arriving found the switch
 * throwing rather than the review declaring an honest limit.
 *
 * <p>It travels with the review as a standing caveat: the model is told what its evidence cannot settle,
 * so it declines rather than guesses, and an operator reading a report can see which questions were out
 * of reach by construction rather than unlucky.
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
