package de.tum.cit.aet.hephaestus.integration.core.signal;

/**
 * Re-offers a pending signal to whatever refused it.
 *
 * <p>The ledger knows a review is owed but nothing about how to ask for one, and the module that can
 * ask depends on the ledger — so the direction is inverted here: the domain that submits reviews
 * contributes one of these per artifact kind it handles.
 *
 * <p>Implementations settle the signal themselves through a {@link SignalRecorder}: triggered when
 * the submission went through, refused again (with the current reason) when it did not. The reaper
 * only decides <em>when</em> to re-offer.
 */
public interface PendingSignalResubmitter {
    /** The artifact kind this resubmitter speaks for. */
    ArtifactKind artifactKind();

    /** Re-attempt the refused submission and record its outcome. */
    void resubmit(ArtifactSignal signal);
}
