package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import java.util.List;

/** Immutable catalog bundled with this build. */
record BundledPracticeCatalog(
    List<BundledEntry<AreaDefinition>> areas,
    List<BundledEntry<PracticeDefinition>> practices
) {
    record BundledEntry<D>(String slug, D definition, int position) {}
}
