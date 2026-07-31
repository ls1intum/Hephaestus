package de.tum.cit.aet.hephaestus.practices.curated;

import java.util.List;
import org.springframework.http.ETag;

record CuratedPracticeVersionPrecondition(List<ETag> candidates) {
    static CuratedPracticeVersionPrecondition parse(String value) {
        List<ETag> candidates = ETag.parse(value);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("If-Match must contain at least one valid entity tag");
        }
        return new CuratedPracticeVersionPrecondition(List.copyOf(candidates));
    }

    boolean matches(long version) {
        ETag current = new ETag("v" + version, false);
        return candidates.stream().anyMatch(candidate -> candidate.isWildcard() || candidate.compare(current, true));
    }
}
