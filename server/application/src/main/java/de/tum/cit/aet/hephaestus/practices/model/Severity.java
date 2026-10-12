package de.tum.cit.aet.hephaestus.practices.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Impact of a real gap against a practice. A NOT_MET result carries one, at least as severe as its most severe
 * problem; each problem carries its own. Other outcomes carry no severity.
 */
@Schema(
        description = "How much a real gap weighs. CRITICAL: act now. MAJOR: fix before the work is done. MINOR: a "
                + "small gap worth raising. INFO (label: Trivial): a real gap with trivial impact; fix it when it is "
                + "cheap.")
public enum Severity {
    /** Must be acted on now — e.g. a leaked secret or a security vulnerability. */
    CRITICAL(0),
    /** A real problem that should be fixed before the work is considered done. */
    MAJOR(1),
    /** A minor issue or style nit; worth raising but not blocking. */
    MINOR(2),
    /** A real gap with trivial impact. Fix it when it is cheap. */
    INFO(3);

    private final int rank;

    Severity(int rank) {
        this.rank = rank;
    }

    /**
     * Position in severity order, {@code 0} for the most severe. Declared rather than taken from the
     * constant order, so reordering the constants cannot reorder feedback.
     */
    public int rank() {
        return rank;
    }
}
