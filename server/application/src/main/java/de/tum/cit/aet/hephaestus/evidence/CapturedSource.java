package de.tum.cit.aet.hephaestus.evidence;

import java.nio.file.Path;
import java.util.Objects;

public record CapturedSource(String path, String mediaType, String sha256, long bytes) {
    public CapturedSource {
        path = requireText(path, "path");
        Path sourcePath = Path.of(path);
        if (sourcePath.isAbsolute()
                || path.contains("\\")
                || sourcePath.normalize().toString().isEmpty()
                || !sourcePath.equals(sourcePath.normalize())) {
            throw new IllegalArgumentException("Source path must be safe and workspace-relative: " + path);
        }
        mediaType = requireText(mediaType, "mediaType");
        sha256 = requireText(sha256, "sha256");
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid SHA-256 digest: " + sha256);
        }
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes must not be negative");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
