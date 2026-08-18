package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactIdentities;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactIdentity;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;

/**
 * Names the work a staged entry refers to, in the terms the developer would recognise.
 *
 * <p>Staged input is read by a model that quotes it back to the person it is about, so a row id in it
 * becomes a number the model presents as one they can look up: work recorded against merge request !22
 * is stored under primary key 306 and reaches the practice pages as "PR #306", pointing at unrelated
 * work or at nothing. Only the number, title, container and link cross into the sandbox — the row id
 * stops here, and a staged file therefore carries no number that is not a number a person can type.
 *
 * <p>Work nobody can name is staged as its kind alone. "A merge request" is a true thing to say about
 * it; its primary key is not, and a bare kind is what leaves the model no number to invent a citation
 * from.
 */
@Component
public class StagedArtifactNames {

    private final ArtifactIdentities identities;

    public StagedArtifactNames(ArtifactIdentities identities) {
        this.identities = identities;
    }

    /** One read per kind — a payload names up to fifty entries and must not query per entry. */
    public Resolved resolve(long workspaceId, Collection<Reference> references) {
        Map<ArtifactKind, Set<Long>> wanted = new LinkedHashMap<>();
        for (Reference reference : references) {
            if (reference.kind() == null || reference.id() == null) {
                continue;
            }
            wanted.computeIfAbsent(reference.kind(), kind -> new LinkedHashSet<>()).add(reference.id());
        }
        Map<ArtifactKind, Map<Long, ArtifactIdentity>> byKind = new LinkedHashMap<>();
        wanted.forEach((kind, ids) -> byKind.put(kind, identities.resolve(workspaceId, kind, ids)));
        return new Resolved(byKind);
    }

    /** One entry's artifact, as the staging side holds it: the ledger coordinates, not a name. */
    public record Reference(@Nullable ArtifactKind kind, @Nullable Long id) {}

    /** The names for one payload's worth of references, resolved together. */
    public static final class Resolved {

        private final Map<ArtifactKind, Map<Long, ArtifactIdentity>> byKind;

        private Resolved(Map<ArtifactKind, Map<Long, ArtifactIdentity>> byKind) {
            this.byKind = byKind;
        }

        /**
         * Writes an {@code artifact} object under {@code node}, or nothing at all when the entry was not
         * filed against any work — an absent object says "this is not about a piece of work", which is a
         * different fact from work that could not be named.
         */
        public void stageInto(ObjectNode node, @Nullable ArtifactKind kind, @Nullable Long id) {
            if (kind == null || id == null) {
                return;
            }
            ObjectNode artifact = node.putObject("artifact");
            artifact.put("kind", kind.value());
            ArtifactIdentity identity = byKind.getOrDefault(kind, Map.of()).get(id);
            if (identity == null) {
                return;
            }
            artifact.put("title", identity.title());
            if (identity.number() != null) {
                artifact.put("number", identity.number());
            }
            if (identity.container() != null) {
                artifact.put("container", identity.container());
            }
            if (identity.url() != null) {
                artifact.put("url", identity.url());
            }
        }
    }
}
