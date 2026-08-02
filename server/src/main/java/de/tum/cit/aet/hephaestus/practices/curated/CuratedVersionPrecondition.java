package de.tum.cit.aet.hephaestus.practices.curated;

import java.util.List;
import org.springframework.http.ETag;

/** Matches writes against content-derived ETags, including entries with no override row. */
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
