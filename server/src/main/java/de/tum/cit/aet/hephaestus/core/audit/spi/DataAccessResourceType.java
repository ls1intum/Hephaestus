package de.tum.cit.aet.hephaestus.core.audit.spi;

/**
 * The kind of disclosure recorded on the data-access trail. Stored as a string so the trail stays
 * readable in raw SQL and a new surface can be added without an ordinal-migration hazard.
 *
 * <p>Every constant must also appear in {@code ck_data_access_event_resource_type} — the CHECK is the
 * storage-layer half of this contract and {@code DataAccessEventImmutabilityIntegrationTest} pins the two
 * together.
 */
public enum DataAccessResourceType {
    /** One named developer's practice report was served (subject = that developer). */
    PRACTICE_REPORT,
    /** The practice-report roster, which names developers, was served (subject = NULL: a bulk view). */
    PRACTICE_ROSTER,
}
