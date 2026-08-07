package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The one place a ledger row's discovery mode becomes the population its measurements belong to. */
@DisplayName("Signal origins")
class SignalOriginsTest extends BaseUnitTest {

    /**
     * The reason this mapping exists at all. A backfill signal refused for budget waits PENDING, is
     * re-offered by the reaper hours later, and the submission it builds carries a trigger signal — which
     * the default rule reads as LIVE. Without this, the tail of every paused campaign would launder
     * itself into the population that is supposed to be free of it.
     */
    @Test
    void aReOfferedBackfillSignalStaysABackfillMeasurement() {
        assertThat(SignalOrigins.observationOriginOf(DiscoveredVia.BACKFILL)).isEqualTo(ObservationOrigin.BACKFILL);
    }

    @Test
    void aHumanAskingByHandIsASelfSelectedSample() {
        assertThat(SignalOrigins.observationOriginOf(DiscoveredVia.MANUAL)).isEqualTo(ObservationOrigin.MANUAL);
    }

    /**
     * Reconciliation never triggers a review on its own; a row it leaves behind is only ever acted on
     * after a live redelivery or a campaign re-records it with its own mode, so LIVE is the honest
     * reading of a signal that reached submission through the event vocabulary.
     */
    @Test
    void whatWeLearnedFromTheProviderIsALiveMeasurement() {
        assertThat(SignalOrigins.observationOriginOf(DiscoveredVia.EVENT)).isEqualTo(ObservationOrigin.LIVE);
        assertThat(SignalOrigins.observationOriginOf(DiscoveredVia.SYNC)).isEqualTo(ObservationOrigin.LIVE);
    }

    /** A new discovery mode must state its population rather than inherit one by accident. */
    @ParameterizedTest
    @EnumSource(DiscoveredVia.class)
    void everyDiscoveryModeNamesAPopulation(DiscoveredVia discoveredVia) {
        assertThat(SignalOrigins.observationOriginOf(discoveredVia)).isNotNull();
    }
}
