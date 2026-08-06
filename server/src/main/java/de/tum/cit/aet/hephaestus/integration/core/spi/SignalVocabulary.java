package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import java.util.Optional;
import java.util.Set;

/**
 * Translates the trigger-event literals practices are still authored against into signal names.
 *
 * <p>One bean per domain module, so the domain that owns a vocabulary owns its translation. That is
 * what lets a new domain make its events bindable without the practices module learning anything about
 * it — the failure this whole contract exists to fix was precisely that adding a document trigger meant
 * editing practices.
 *
 * <p>Transitional by design. {@code practice.trigger_events} holds strings like {@code "PullRequestReady"}
 * that predate signal names and are persisted, so the two vocabularies coexist until bindings name
 * signals directly; at that point this port loses its only caller and goes.
 */
public interface SignalVocabulary {
    /** The signal a persisted trigger-event literal means, or empty if this domain does not own it. */
    Optional<SignalName> signalForTriggerEvent(String triggerEventName);

    /** Every trigger-event literal this domain translates. */
    Set<String> triggerEventNames();
}
