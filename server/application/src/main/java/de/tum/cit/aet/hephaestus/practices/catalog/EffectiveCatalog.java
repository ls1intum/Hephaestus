package de.tum.cit.aet.hephaestus.practices.catalog;

import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record EffectiveCatalog(
        List<CatalogOffer<GroupDefinition>> groups,
        List<CatalogOffer<CatalogPracticeDocument>> practices,
        String etag) {
    public EffectiveCatalog {
        groups = List.copyOf(groups);
        Set<String> offeredGroups = groups.stream()
                .filter(CatalogOffer::offered)
                .map(CatalogOffer::slug)
                .collect(Collectors.toUnmodifiableSet());
        practices = practices.stream()
                .map(entry -> {
                    String group = entry.effective().groupSlug();
                    return new CatalogOffer<>(
                            entry.slug(),
                            entry.kind(),
                            entry.effective(),
                            entry.shipped(),
                            entry.base(),
                            entry.position(),
                            entry.excluded(),
                            entry.state(),
                            entry.changeKind(),
                            entry.etag(),
                            !entry.excluded() && (group == null || offeredGroups.contains(group)));
                })
                .toList();
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
        return practices.stream().filter(CatalogOffer::offered).toList();
    }
}
