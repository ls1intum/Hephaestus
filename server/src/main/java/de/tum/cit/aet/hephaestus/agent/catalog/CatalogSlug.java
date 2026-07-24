package de.tum.cit.aet.hephaestus.agent.catalog;

import java.text.Normalizer;
import java.util.Locale;

/** Internal stable identifier derived from a human-facing name. */
final class CatalogSlug {

    private CatalogSlug() {}

    static String from(String value) {
        String slug = Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
        if (slug.isEmpty()) slug = "item";
        return slug.substring(0, Math.min(63, slug.length())).replaceAll("-+$", "");
    }

    static String suffix(String base, int number) {
        String suffix = "-" + number;
        return base.substring(0, Math.min(base.length(), 63 - suffix.length())).replaceAll("-+$", "") + suffix;
    }
}
