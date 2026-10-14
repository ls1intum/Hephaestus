package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import de.tum.cit.aet.hephaestus.practices.catalog.CatalogPracticeDocument;
import java.util.List;

public record BundledPracticeCatalog(
        List<BundledEntry<GroupDefinition>> groups, List<BundledEntry<CatalogPracticeDocument>> practices) {
    public BundledPracticeCatalog {
        groups = List.copyOf(groups);
        practices = List.copyOf(practices);
    }

    public record BundledEntry<D>(String slug, D definition, int position) {}
}
