package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.practices.model.ResultOrigin;

/**
 * How a signal's discovery mode maps onto the population its measurements belong to.
 *
 * <p>{@code DiscoveredVia} answers how we learned the signal happened; {@code ResultOrigin} answers
 * how the sample was selected. A review submitted from a ledger row has to derive the second from the
 * first, because by then nothing else remembers.
 */
public final class SignalOrigins {

    private SignalOrigins() {}

    public static ResultOrigin practiceResultOriginOf(DiscoveredVia discoveredVia) {
        return switch (discoveredVia) {
            // SYNC groups with LIVE despite finding a signal after the fact: reconciliation never triggers a
            // review on its own. A live redelivery, campaign or continuation of an authorized event selects it.
            //
            // SWEEP groups with LIVE, not BACKFILL: its window is bounded to the recent past by rule rather
            // than chosen with hindsight, so filing it as BACKFILL would hide its results from the
            // reflection read model.
            case EVENT, SYNC, SWEEP -> ResultOrigin.LIVE;
            case MANUAL -> ResultOrigin.MANUAL;
            case BACKFILL -> ResultOrigin.BACKFILL;
        };
    }
}
