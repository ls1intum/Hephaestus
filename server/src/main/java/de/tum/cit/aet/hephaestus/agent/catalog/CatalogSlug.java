package de.tum.cit.aet.hephaestus.agent.catalog;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

/** Internal stable identifier derived from a human-facing name. */
final class CatalogSlug {

    private CatalogSlug() {}

    /**
     * The slug to store for a newly created catalog row.
     *
     * <p>A caller-supplied slug is taken as given — including one that is already taken. The uniqueness
     * scopes differ per entity ({@code taken} carries that), and every one of them is backed by a UNIQUE
     * constraint, so the loser of a concurrent create is reported as a conflict rather than silently
     * renamed behind the caller's back. Only a slug WE derived from a display name may be renamed,
     * because nobody asked for that one.
     */
    static String unique(@Nullable String requested, String displayName, Predicate<String> taken) {
        if (StringUtils.hasText(requested)) return requested;
        String base = from(displayName);
        String candidate = base;
        for (int i = 2; taken.test(candidate); i++) candidate = suffix(base, i);
        return candidate;
    }

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
