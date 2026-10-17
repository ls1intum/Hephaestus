package de.tum.cit.aet.hephaestus.practices;

import java.io.Serial;

public final class StalePracticeVersionException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String currentVersionId;

    public StalePracticeVersionException(String currentVersionId) {
        super("The practice version changed. Reload it before saving.");
        this.currentVersionId = currentVersionId;
    }

    public String currentVersionId() {
        return currentVersionId;
    }
}
