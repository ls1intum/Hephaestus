package de.tum.cit.aet.hephaestus.practices.spi;

public enum ReviewStatus {
    QUEUED,
    RUNNING,
    COMPLETE,
    PARTIAL,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETE || this == PARTIAL || this == FAILED || this == CANCELLED;
    }
}
