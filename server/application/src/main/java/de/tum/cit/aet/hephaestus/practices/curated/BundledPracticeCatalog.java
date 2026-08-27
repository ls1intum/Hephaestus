package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import java.util.List;

record BundledPracticeCatalog(
        List<BundledEntry<GroupDefinition>> groups, List<BundledEntry<PracticeDefinition>> practices) {
    BundledPracticeCatalog {
        groups = List.copyOf(groups);
        practices = List.copyOf(practices);
    }

    record BundledEntry<D>(String slug, D definition, int position) {}
}
