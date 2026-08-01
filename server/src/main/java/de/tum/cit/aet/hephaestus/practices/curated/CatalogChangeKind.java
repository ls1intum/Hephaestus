package de.tum.cit.aet.hephaestus.practices.curated;

/**
 * What separates an administrator's definition from the one Hephaestus ships now — the difference
 * that decides how carefully an update needs looking at.
 *
 * <p>The line is drawn where it already exists in the code: the detection fingerprint covers
 * everything a detection run reads, so a difference outside it cannot change what gets detected.
 */
public enum CatalogChangeKind {
    /** The two agree; nothing to weigh up. */
    NONE,
    /** Only the copy people read differs. Taking the update cannot change what gets detected. */
    WORDING,
    /** The criteria, script, triggers or filing differ. Taking the update changes what gets detected. */
    DETECTION,
}
