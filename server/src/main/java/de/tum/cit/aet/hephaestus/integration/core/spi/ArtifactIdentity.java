package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * How a person recognises one artifact: the number they typed, the title they read, where it lives.
 *
 * <p>Everything else about an artifact reaches the practices module through the signal ledger, which
 * stores nothing but {@code (kind, id)} — deliberately, because a ledger that copied titles would have
 * to keep them in step with the mirror forever. That is fine for deciding whether to review something
 * and useless for telling somebody what was reviewed: {@code scm.pull_request #48211} is a row id, not
 * a merge request. This record is the one thing core asks a domain module to translate.
 *
 * <p>Deliberately not the same type as {@code ReviewRunTargetLookup.Target}, which answers the adjacent
 * question "what was this <em>job</em> about" by decoding the metadata the job carries. That answer
 * cannot exist for an artifact no job ran on — which is precisely the case a trace exists to explain.
 *
 * @param kind      the family the artifact belongs to
 * @param id        the mirror's identifier, as the ledger stores it
 * @param number    the number the provider shows, when the kind has one; a document does not
 * @param title     a human label; never blank, so a caller never has to invent one
 * @param container the repository, collection or channel it sits in, when the kind has one
 * @param url       where to open it upstream, when we can build a whole one
 */
public record ArtifactIdentity(
    @NonNull ArtifactKind kind,
    @NonNull Long id,
    @Nullable Integer number,
    @NonNull String title,
    @Nullable String container,
    @Nullable String url
) {
    public ArtifactIdentity {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        if (title.isBlank()) {
            throw new IllegalArgumentException("An artifact identity needs a title; use the kind's display name");
        }
    }

    /** The identity of something we can name only by its kind — the honest answer, not a missing one. */
    public static ArtifactIdentity unresolved(ArtifactKind kind, Long id, String kindDisplayName) {
        return new ArtifactIdentity(kind, id, null, kindDisplayName, null, null);
    }
}
