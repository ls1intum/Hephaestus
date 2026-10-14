package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record ScanExportLocation(
        String path,
        @Nullable Integer startLine,
        @Nullable Integer endLine,
        @Nullable String quote) {
    public ScanExportLocation {
        if (path.isBlank()
                || path.startsWith("/")
                || path.contains("\\")
                || List.of(path.split("/", -1)).contains("..")
                || (startLine != null && startLine < 1)
                || (endLine != null && (startLine == null || endLine < startLine))
                || (quote != null && startLine == null))
            throw new IllegalArgumentException("Invalid exported citation");
    }
}
