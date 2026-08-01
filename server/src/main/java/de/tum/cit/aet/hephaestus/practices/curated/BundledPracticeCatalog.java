package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import java.util.List;

/**
 * The catalog this build ships, parsed from the classpath.
 *
 * <p>It carries no version of its own. Nothing is ever "applied", so there is no applied-once state
 * to order or to conflict: what an instance offers is computed from this and its override rows every
 * time it is asked.
 */
record BundledPracticeCatalog(
    List<BundledEntry<AreaDefinition>> areas,
    List<BundledEntry<PracticeDefinition>> practices
) {
    /** One shipped entry: its durable slug and the definition Hephaestus ships under it. */
    record BundledEntry<D>(String slug, D definition) {}
}
