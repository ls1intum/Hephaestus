package de.tum.cit.aet.hephaestus.practices.curated;

/** What taking the Hephaestus definition would change. */
public enum CatalogChangeKind {
    /** The two agree; nothing to weigh up. */
    NONE,
    /** Only the copy people read differs. Taking the update cannot change what gets detected. */
    WORDING,
    /** An area's name, description, icon or color differs. Areas present practices; they do not detect. */
    PRESENTATION,
    /** The criteria, script, triggers or filing differ. Taking the update changes what gets detected. */
    DETECTION,
}
