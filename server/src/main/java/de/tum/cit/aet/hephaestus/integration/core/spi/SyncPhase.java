package de.tum.cit.aet.hephaestus.integration.core.spi;

/**
 * The phase a sync runner is currently in — the machine token behind {@link SyncProgress#phase()}.
 *
 * <p>Multi-phase syncs (org → repositories → issues → pull requests → comments → teams) are otherwise
 * indistinguishable from the outside: the job reports a moving item count with no indication of
 * <em>what</em> is moving. The token lets the UI render a phase chip and highlight the active step;
 * {@link SyncProgress#currentStep()} carries the human sentence.
 *
 * <p>Each integration reports only the phases it actually has. Nothing here is mandatory.
 */
public enum SyncPhase {
    /** Organization / group / workspace-level metadata. */
    ORGANIZATION("organization"),
    /** Repository / project enumeration and metadata. */
    REPOSITORIES("repositories"),
    /** Issue history. */
    ISSUES("issues"),
    /** Pull-request / merge-request history. */
    PULL_REQUESTS("pullRequests"),
    /** Issue comments, review comments, and threads. */
    COMMENTS("comments"),
    /** Commit history. */
    COMMITS("commits"),
    /** Teams and memberships. */
    TEAMS("teams"),
    /** Slack channels. */
    CHANNELS("channels"),
    /** Outline collections. */
    COLLECTIONS("collections");

    private final String token;

    SyncPhase(String token) {
        this.token = token;
    }

    /** Stable wire token — this is what lands in the job's {@code progress} JSONB, not {@link #name()}. */
    public String token() {
        return token;
    }
}
