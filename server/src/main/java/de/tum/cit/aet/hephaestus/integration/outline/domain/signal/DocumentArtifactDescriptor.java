package de.tum.cit.aet.hephaestus.integration.outline.domain.signal;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ActorRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackLane;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ReviewLimitation;
import de.tum.cit.aet.hephaestus.integration.core.spi.Signal;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * What can happen to a written document, and which of those a practice may be reviewed on.
 *
 * <p>This is the descriptor the whole contract was built to make possible. Outline has had a complete
 * webhook stack and eleven ingested lifecycle events for as long as it has existed here, and until now
 * not one of them could start anything: the practices module had to know a kind existed before a
 * practice could be bound to it, so adding document triggering meant editing practices. Nothing in
 * this file is known to that module — it reads the registered descriptors and offers whatever it finds.
 *
 * <p>Unconditional, like every other descriptor. What a document is, and what can happen to one, is a
 * fact about the domain; whether this deployment has Outline switched on is a fact about the
 * deployment, and it is answered by {@code OutlineManifest.enabled()} and by whether a workspace has a
 * connection. A descriptor gated on a feature flag would make the vocabulary itself configuration, and
 * a practice bound to a document would stop parsing when an operator turned the integration off.
 *
 * <p><b>Lanes.</b> Only {@link FeedbackLane#PROFILE}. Outline's API can post a comment on a document,
 * but nothing here does, and declaring a lane no channel fills is the same aspirational declaration
 * this branch has spent ten slices deleting. Feedback about a document therefore lands where
 * Hephaestus owns the surface, and the day a document comment channel exists this line and the
 * manifest's {@code delivers} change together.
 */
@Component
public class DocumentArtifactDescriptor implements ArtifactDescriptor {

    /**
     * Every document event arrives on one registry key.
     *
     * <p>{@code OutlineSubjectParser} collapses all eleven subscribed events onto
     * {@code EventTypeKey(OUTLINE, "document")}, so that key — not the Outline event name — is the unit
     * of provenance the framework can check a handler for. Which of those events raises which signal is
     * stated in {@link DocsSignals#forOutlineEvent(String)}, next to the names the sync path already
     * switches on, rather than duplicated here as a second mapping that could drift.
     */
    private static final EventTypeKey OUTLINE_DOCUMENT = new EventTypeKey(IntegrationKind.OUTLINE, "document");

    private static final List<Signal> SIGNALS = List.of(
        declare(DocsSignals.DOCUMENT_PUBLISHED, "Published", true),
        declare(DocsSignals.DOCUMENT_UPDATED, "Content changed", true),
        declare(DocsSignals.DOCUMENT_ARCHIVED, "Archived", false)
    );

    @Override
    public ArtifactKind kind() {
        return DocsSignals.DOCUMENT;
    }

    @Override
    public String displayName() {
        return "Document";
    }

    @Override
    public List<Signal> signals() {
        return SIGNALS;
    }

    /**
     * Only an author. A document has no assignee and no reviewer — Outline's revision history names
     * everyone who has edited it, but "has edited" is not a role a review can attribute an observation
     * to, and inventing one would put a co-editor's name on a judgement about somebody else's writing.
     */
    @Override
    public Set<ActorRole> roles() {
        return Set.of(ActorRole.AUTHOR);
    }

    @Override
    public Set<FeedbackLane> lanes() {
        return Set.of(FeedbackLane.PROFILE);
    }

    /**
     * A document is a claim about the world, and reading it establishes only what it claims. Whether the
     * system it describes actually behaves that way is not in the document, and neither is whether the
     * document is the one people actually read — a wiki keeps the abandoned draft next to the live page.
     */
    @Override
    public List<ReviewLimitation> reviewLimitations() {
        return List.of(
            new ReviewLimitation(
                "DESCRIBED_SYSTEM_NOT_OBSERVED",
                "Document evidence does not establish whether the system it describes behaves as written."
            ),
            new ReviewLimitation(
                "READERSHIP_NOT_OBSERVED",
                "The mirrored document does not establish whether anyone read it or relied on it."
            )
        );
    }

    @Override
    public boolean reviewable() {
        return true;
    }

    private static Signal declare(SignalName name, String displayName, boolean recommended) {
        return new Signal(name, displayName, Set.of(OUTLINE_DOCUMENT), DocsSignals.revisionScheme(name), recommended);
    }
}
