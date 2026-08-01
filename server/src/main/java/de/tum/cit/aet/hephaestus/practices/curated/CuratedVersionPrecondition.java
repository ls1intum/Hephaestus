package de.tum.cit.aet.hephaestus.practices.curated;

import java.util.List;
import org.springframework.http.ETag;

/**
 * The entity tag a catalog write must be based on.
 *
 * <p>Derived from the entry's content rather than a row version, because an entry nobody has touched
 * has no row — and the first edit of one is exactly the case where two administrators are most likely
 * to collide.
 */
record CuratedVersionPrecondition(List<ETag> candidates) {
    static CuratedVersionPrecondition parse(String value) {
        List<ETag> candidates = ETag.parse(value);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("If-Match must contain at least one valid entity tag");
        }
        return new CuratedVersionPrecondition(List.copyOf(candidates));
    }

    boolean matches(String tag) {
        ETag current = new ETag(tag, false);
        return candidates.stream().anyMatch(candidate -> candidate.isWildcard() || candidate.compare(current, true));
    }
}
