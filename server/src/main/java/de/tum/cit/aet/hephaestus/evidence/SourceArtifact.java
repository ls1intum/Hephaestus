package de.tum.cit.aet.hephaestus.evidence;

import java.util.Objects;

public record SourceArtifact(String path, String mediaType, String sha256, long bytes) {
    public SourceArtifact {
        path = requireText(path, "path");
        if (path.startsWith("/") || path.contains("\\") || path.contains("../") || path.equals("..")) {
            throw new IllegalArgumentException("Artifact path must be safe and workspace-relative: " + path);
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
