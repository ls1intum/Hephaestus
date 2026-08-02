package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import java.util.List;

record BundledPracticeCatalog(
    List<BundledEntry<AreaDefinition>> areas,
    List<BundledEntry<PracticeDefinition>> practices
) {
    BundledPracticeCatalog {
        areas = List.copyOf(areas);
        practices = List.copyOf(practices);
    }

    record BundledEntry<D>(String slug, D definition, int position) {}
}
