package de.tum.cit.aet.hephaestus.evidence;

import java.util.Objects;

/**
 * Auditable result for one subject clause.
 *
 * @param aspect   which staged fact the clause read
 * @param readFrom the source that answered it
 * @param finding  what looking established
 */
public record SubjectClauseFinding(SubjectAspect aspect, SourceKind readFrom, SubjectFinding finding) {
    public SubjectClauseFinding {
        Objects.requireNonNull(aspect, "aspect");
        Objects.requireNonNull(readFrom, "readFrom");
        Objects.requireNonNull(finding, "finding");
    }
}
