package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import java.util.List;
import org.jspecify.annotations.Nullable;

record BundledPracticeCatalog(
    long catalogRevision,
    String contentDigest,
    List<BundledArea> areas,
    List<BundledPractice> practices
) {
    record BundledArea(
        String slug,
        String name,
        @Nullable String description,
        int displayOrder,
        @Nullable String icon,
        @Nullable String color
    ) {}

    record BundledPractice(String slug, PracticeDefinition definition, String definitionDigest) {}
}
