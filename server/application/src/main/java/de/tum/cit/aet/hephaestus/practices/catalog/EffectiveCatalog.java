package de.tum.cit.aet.hephaestus.practices.catalog;

import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import java.util.List;
import java.util.Optional;

public record EffectiveCatalog(
        List<CatalogOffer<GroupDefinition>> groups,
        List<CatalogOffer<CatalogPracticeDocument>> practices,
        String etag) {
    public EffectiveCatalog {
        groups = List.copyOf(groups);
        practices = List.copyOf(practices);
    }

    public Optional<CatalogOffer<CatalogPracticeDocument>> practice(String slug) {
        return practices.stream().filter(entry -> entry.slug().equals(slug)).findFirst();
    }

    public Optional<CatalogOffer<GroupDefinition>> group(String slug) {
        return groups.stream().filter(entry -> entry.slug().equals(slug)).findFirst();
    }

    public List<CatalogOffer<GroupDefinition>> installableGroups() {
        return groups.stream().filter(CatalogOffer::offered).toList();
    }

    public List<CatalogOffer<CatalogPracticeDocument>> installablePractices() {
        return practices.stream()
                .filter(CatalogOffer::offered)
                .filter(entry -> {
                    String group = entry.effective().groupSlug();
                    return group == null
                            || group(group).map(CatalogOffer::offered).orElse(false);
                })
                .toList();
    }
}
