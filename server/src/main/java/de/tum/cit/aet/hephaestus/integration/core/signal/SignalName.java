package de.tum.cit.aet.hephaestus.integration.core.signal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Name of something observable that happened to an artifact, e.g. {@code scm.pull_request.merged}.
 *
 * <p>Carries its {@link ArtifactKind} as a prefix, derived rather than declared a second time, so a
 * practice's watched signals cannot disagree with its watched artifacts. Same grammar and {@code ':'}
 * prohibition as {@link ArtifactKind}: these strings are persisted vocabulary, and renaming one is a data
 * migration. Crosses the API and lands in {@code practice.bindings} as the bare string, never a wrapper
 * object.
 */
public record SignalName(String value) {
    /** Fits {@code artifact_signal.signal_name}. */
    static final int MAX_LENGTH = 128;

    private static final Pattern GRAMMAR = Pattern.compile("[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*");

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public SignalName {
        Objects.requireNonNull(value, "signal name must not be null");
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("signal name exceeds " + MAX_LENGTH + " characters: " + value);
        }
        if (!GRAMMAR.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "signal name must be <domain>.<kind>.<signal> in lowercase snake_case, got: " + value
            );
        }
    }

    public static SignalName of(String value) {
        return new SignalName(value);
    }

    @Override
    @JsonValue
    public String value() {
        return value;
    }

    /** Read off the name's prefix; never serialized, since {@link #value()} already carries it on the wire. */
    @JsonIgnore
    public ArtifactKind artifactKind() {
        return ArtifactKind.of(value.substring(0, value.lastIndexOf('.')));
    }

    @Override
    public String toString() {
        return value;
    }
}
