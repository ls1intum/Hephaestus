package de.tum.cit.aet.hephaestus.practices.curated.adoption;

/** What adopting a catalog practice does about the group it belongs to. */
public enum CatalogAreaDisposition {
    /** The catalog entry names no group, so the copy lands ungrouped. */
    UNASSIGNED,
    /** A workspace group already holds that slug and is used as-is; its name and visuals are untouched. */
    REUSE_EXISTING_AREA,
    /** No workspace group holds that slug, so adoption creates one from the catalog's definition. */
    CREATE_CATALOG_AREA,
}
