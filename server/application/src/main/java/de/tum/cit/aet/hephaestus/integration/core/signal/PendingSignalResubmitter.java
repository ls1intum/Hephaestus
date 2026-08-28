package de.tum.cit.aet.hephaestus.integration.core.signal;

/**
 * Re-offers a pending signal to whatever refused it.
 *
 * <p>The ledger knows a review is owed but not how to ask for one, so this inverts the dependency: the
 * domain that submits reviews contributes one of these per artifact kind. Implementations settle the
 * signal themselves through a {@link SignalRecorder} — triggered on success, refused again on failure —
 * leaving the reaper to decide only <em>when</em> to re-offer.
 */
public interface PendingSignalResubmitter {
    ArtifactKind artifactKind();

    void resubmit(ArtifactSignal signal);
}
