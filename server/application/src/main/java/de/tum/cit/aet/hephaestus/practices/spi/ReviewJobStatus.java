package de.tum.cit.aet.hephaestus.practices.spi;

public enum ReviewJobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    TIMED_OUT,
    CANCELLED;
}
