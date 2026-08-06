package de.tum.cit.aet.hephaestus.evidence;

/**
 * How a source's capture is anchored in time.
 *
 * <p>Deliberately two values. An age-based mode was considered and rejected: nothing in the capture
 * pipeline can distinguish "collected an hour ago and unchanged" from "collected an hour ago and
 * since superseded", so a staleness verdict would have been a guess wearing a contract's clothes.
 */
public enum IdentityMode {
    /** The capture names something immutable — a commit SHA, a revision id — so it cannot drift. */
    PINNED_IDENTITY,
    /** The capture reflects state that moves independently; no identity anchors it. */
    NOT_APPLICABLE,
}
