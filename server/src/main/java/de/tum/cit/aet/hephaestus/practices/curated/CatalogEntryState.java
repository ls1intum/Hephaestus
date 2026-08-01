package de.tum.cit.aet.hephaestus.practices.curated;

/**
 * How one catalog entry stands, derived from the shipped definition and the administrator's override.
 * Nothing stores this — it is read off the two inputs every time, so it cannot go stale.
 */
public enum CatalogEntryState {
    /** Nobody has touched it. It is what this build ships, and a newer build simply updates it. */
    FROM_HEPHAESTUS,
    /** An administrator replaced it, and it still matches the shipped definition they wrote it against. */
    EDITED_HERE,
    /** An administrator replaced it, and Hephaestus has shipped a different definition since. */
    UPDATE_WAITING,
    /** Written on this instance; Hephaestus ships nothing under this slug. */
    YOURS,
    /** Hephaestus used to ship this and no longer does. The instance's own definition still stands. */
    NO_LONGER_SHIPPED,
}
