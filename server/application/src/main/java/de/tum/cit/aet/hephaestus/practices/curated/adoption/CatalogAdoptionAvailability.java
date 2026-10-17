package de.tum.cit.aet.hephaestus.practices.curated.adoption;

/** Whether a catalog practice can still be adopted into this workspace. */
public enum CatalogAdoptionAvailability {
    /** Offered by the instance catalog and not yet copied here. */
    AVAILABLE,
    /** An adopted copy is independent; adopting it again would duplicate the workspace practice. */
    ADOPTED,
    /**
     * A different workspace practice already holds this slug. Slug identity is what prevents
     * duplicate adoption, so the entry cannot be added until one of the two is renamed.
     */
    SLUG_CONFLICT,
}
