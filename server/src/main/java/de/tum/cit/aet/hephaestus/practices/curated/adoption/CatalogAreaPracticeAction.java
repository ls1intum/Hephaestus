package de.tum.cit.aet.hephaestus.practices.curated.adoption;

/** What adopting a whole catalog group would do to one practice in it. */
public enum CatalogAreaPracticeAction {
    /** Not in the workspace yet: adoption copies it in. */
    ADD,
    /** Already here under a different group: adoption moves it, leaving the definition alone. */
    MOVE_TO_AREA,
    /** Already here in this group: adoption leaves it exactly as it is. */
    KEEP,
    /** Cannot be added — see {@link CatalogAdoptionAvailability#SLUG_CONFLICT}. */
    BLOCKED,
}
