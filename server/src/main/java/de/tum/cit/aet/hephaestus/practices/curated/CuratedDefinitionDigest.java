package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.CatalogDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;

final class CuratedDefinitionDigest {

    private static final String AREA_V1 = "area:v1:";
    private static final String PRACTICE_V2 = "practice:v2:";

    private CuratedDefinitionDigest() {}

    static String of(String slug, CatalogDefinition definition) {
        String prefix = switch (definition) {
            case AreaDefinition ignored -> AREA_V1;
            case PracticeDefinition ignored -> PRACTICE_V2;
            default -> throw new IllegalArgumentException("Unsupported catalog definition: " + definition.getClass());
        };
        return prefix + definition.digest(slug);
    }
}
