package de.tum.cit.aet.hephaestus.integration.core.signal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Identifier of a family of reviewable artifacts, e.g. {@code scm.pull_request}.
 *
 * <p>Core owns the grammar and the enforcement; the domain-owning module owns the meaning. Kinds are
 * vendor-neutral, so nothing about a provider may leak into the identifier — that is what lets one
 * practice hold across every provider of a domain.
 *
 * <p>It is also the <em>only</em> name for what a practice observes, what an agent job is about, and
 * what an observation is filed against. A second, per-module vocabulary would drift, and the switch
 * translating between the two becomes the place every new domain has to be registered twice.
 *
 * <p>The grammar is narrower than "any string" because these values are persisted and outlive the code
 * that wrote them: whatever the parser accepts it must keep accepting. In particular {@code ':'} can
 * never appear — agent-job idempotency keys are colon-delimited and
 * {@link de.tum.cit.aet.hephaestus.agent.job.AgentJobService} splits them on the last colon, so a kind
 * carrying one would silently re-scope every cooldown derived from it.
 */
public record ArtifactKind(String value) {
    /** Fits {@code artifact_signal.artifact_kind} and every other {@code artifact_kind} column. */
    public static final int MAX_LENGTH = 64;

    private static final Pattern GRAMMAR = Pattern.compile("[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*");

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public ArtifactKind {
        Objects.requireNonNull(value, "artifact kind must not be null");
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("artifact kind exceeds " + MAX_LENGTH + " characters: " + value);
        }
        if (!GRAMMAR.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "artifact kind must be <domain>.<kind> in lowercase snake_case, got: " + value
            );
        }
    }

    public static ArtifactKind of(String value) {
        return new ArtifactKind(value);
    }

    /**
     * The wire and storage form: a bare string, never an object wrapping one, so a payload, a row and a
     * log line all spell a kind the same way.
     */
    @Override
    @JsonValue
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
