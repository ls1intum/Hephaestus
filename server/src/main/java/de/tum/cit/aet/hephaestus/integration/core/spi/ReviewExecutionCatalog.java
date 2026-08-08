package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import java.util.Set;

/**
 * The kinds of work a review can actually be <em>run</em> on in this build.
 *
 * <p>Every other rule in the review contract checks one declaration against another: a kind names its
 * signals, its roles, its lanes, what its evidence cannot settle, and a builder claims it can assemble
 * the context. A kind can satisfy all of that with no job type to submit, no handler to prepare inputs
 * and nothing to deliver — and a workspace then sees a bundled practice as live that can never fire.
 *
 * <p>This is the falsifier. The execution machinery — job types and their registered handlers — lives
 * downstream of the contract and cannot be named from here, so it reports back through this seam
 * instead, and {@code ReviewContractValidator} refuses to start a build whose reviewable kinds it does
 * not cover.
 *
 * <p>Deliberately a set of kinds and not a lookup: the contract has no business knowing what a job type
 * is, only whether <em>something</em> can run a review of the kind a descriptor declares reviewable.
 */
public interface ReviewExecutionCatalog {
    /** The artifact kinds this build can submit and run a practice review for. */
    Set<ArtifactKind> executableKinds();
}
