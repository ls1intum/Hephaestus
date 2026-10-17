package de.tum.cit.aet.hephaestus.evidence.capture;

import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A domain-captured text location, including the version and digest of its staged bytes. */
public record CitableText(
        String source,
        String path,
        CitationSide side,
        @Nullable String commit,
        String stagedPath,
        String sha256) {
    public CitableText {
        new SourceKind(source);
        Objects.requireNonNull(side);
        if (path.isBlank()
                || path.length() > 4096
                || stagedPath.isBlank()
                || stagedPath.contains("\\")
                || Path.of(stagedPath).isAbsolute()
                || !Path.of(stagedPath).normalize().equals(Path.of(stagedPath))
                || Path.of(stagedPath).startsWith("..")
                || !sha256.matches("[0-9a-f]{64}")
                || (commit != null && (commit.isBlank() || commit.length() > 1024))) {
            throw new IllegalArgumentException("Invalid citable text");
        }
    }
}
