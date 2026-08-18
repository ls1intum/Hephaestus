package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import java.util.Set;

/**
 * The kinds of work a review can actually be <em>run</em> on in this build.
 *
 * <p>A kind can satisfy every other rule in the review contract — signals, roles, lanes, evidence — with
 * no job type, handler, or delivery path behind it, leaving a bundled practice that can never fire;
 * {@code ReviewContractValidator} uses this catalog to refuse to start a build whose reviewable kinds it
 * does not cover. Deliberately a set, not a lookup: the contract only needs to know whether
 * <em>something</em> can run a kind, not what.
 */
public interface ReviewExecutionCatalog {
    Set<ArtifactKind> executableKinds();
}
