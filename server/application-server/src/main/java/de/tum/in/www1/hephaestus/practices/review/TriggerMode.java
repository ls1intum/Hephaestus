package de.tum.in.www1.hephaestus.practices.review;

/**
 * How the practice review was triggered — determines which workspace-level
 * toggle is checked by {@link PracticeReviewDetectionGate}.
 */
public enum TriggerMode {
    /** Event-driven (PR created, synchronized, review submitted). */
    AUTO,
    /** Explicit user action (bot command {@code /hephaestus review}). */
    MANUAL
}
