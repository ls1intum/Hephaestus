package de.tum.cit.aet.hephaestus.integration.core.spi;

/** A listed item that cannot supply the text required for a scan. */
public enum ItemExclusionReason {
    FILE_TOO_LARGE,
    BINARY_FILE
}
