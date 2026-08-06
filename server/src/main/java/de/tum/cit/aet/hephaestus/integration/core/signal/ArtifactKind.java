package de.tum.cit.aet.hephaestus.integration.core.signal;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Identifier of a family of reviewable artifacts, e.g. {@code scm.pull_request}.
 *
 * <p>Core owns the grammar and the enforcement; the domain-owning module owns the meaning. Kinds are
 * vendor-neutral on purpose — a practice that watches {@code scm.pull_request} works on GitHub and
 * GitLab alike, so nothing about a provider may leak into the identifier.
 *
 * <p>The grammar is deliberately narrower than "any string" because these values are persisted in
 * {@code artifact_signal.artifact_kind} and outlive the code that wrote them: whatever the parser
 * accepts today it must keep accepting forever. In particular {@code ':'} can never appear —
 * agent-job idempotency keys are colon-delimited and
 * {@link de.tum.cit.aet.hephaestus.agent.job.AgentJobService} splits them on the last colon, so a
 * kind carrying one would silently re-scope every cooldown derived from it.
 */
public record ArtifactKind(String value) {
    /** Fits {@code artifact_signal.artifact_kind}. */
    static final int MAX_LENGTH = 64;

    private static final Pattern GRAMMAR = Pattern.compile("[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*");

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

    @Override
    public String toString() {
        return value;
    }
}
