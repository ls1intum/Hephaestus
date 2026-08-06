package de.tum.cit.aet.hephaestus.integration.core.signal;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Name of something observable that happened to an artifact, e.g. {@code scm.pull_request.merged}.
 *
 * <p>The name carries its {@link ArtifactKind} as a prefix, so the kind is <em>derived</em> rather
 * than declared a second time; a practice that states which signals it watches has thereby also
 * stated which artifacts it watches, and the two can never disagree.
 *
 * <p>Same grammar rules and the same {@code ':'} prohibition as {@link ArtifactKind}: these strings
 * are persisted vocabulary, and renaming one is a data migration.
 */
public record SignalName(String value) {
    /** Fits {@code artifact_signal.signal_name}. */
    static final int MAX_LENGTH = 128;

    private static final Pattern GRAMMAR = Pattern.compile("[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*");

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

    /** The artifact family this signal is about, read off the name's prefix. */
    public ArtifactKind artifactKind() {
        return ArtifactKind.of(value.substring(0, value.lastIndexOf('.')));
    }

    @Override
    public String toString() {
        return value;
    }
}
