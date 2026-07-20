package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;

/**
 * Whether a practice is active — the flag {@code PracticeReviewDetectionGate} reads to decide whether
 * the practice is reviewed at all. Lives here rather than beside the workspace snapshots because the
 * flag belongs to this module.
 */
public record PracticeActiveSnapshot(boolean active) implements ConfigAuditSnapshot {}
