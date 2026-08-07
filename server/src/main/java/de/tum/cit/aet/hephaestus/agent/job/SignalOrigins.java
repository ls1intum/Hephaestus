package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;

/**
 * How a signal's discovery mode maps onto the population its measurements belong to.
 *
 * <p>The two vocabularies exist because they answer different questions — {@code DiscoveredVia} is about
 * how we learned the signal happened, {@code ObservationOrigin} about how the sample was selected — but a
 * review submitted from a ledger row has to derive the second from the first, because by then nothing
 * else remembers.
 *
 * <p>Without this, the pending-signal reaper is a laundering path: a backfill signal refused for budget
 * sits PENDING, gets re-offered hours later, and the submission it builds carries a trigger signal, which
 * the default rule reads as LIVE. The campaign's tail would then quietly land in the population that is
 * supposed to be free of it.
 */
public final class SignalOrigins {

    private SignalOrigins() {}

    /** The population a review occasioned by a signal discovered this way measures. */
    public static ObservationOrigin observationOriginOf(DiscoveredVia discoveredVia) {
        return switch (discoveredVia) {
            // Reconciliation never triggers a review on its own; a row it left behind is only ever acted
            // on by a live redelivery or a campaign, each of which re-records with its own mode first.
            case EVENT, SYNC -> ObservationOrigin.LIVE;
            case MANUAL -> ObservationOrigin.MANUAL;
            case BACKFILL -> ObservationOrigin.BACKFILL;
        };
    }
}
