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
     * A backfill signal re-offered by the reaper still builds a submission that carries a trigger signal the
     * default rule would read as LIVE; without this override the tail of a paused campaign launders into
     * the population that is supposed to be free of it.
     */
    @Test
    void aReOfferedBackfillSignalStaysABackfillMeasurement() {
        assertThat(SignalOrigins.observationOriginOf(DiscoveredVia.BACKFILL)).isEqualTo(ObservationOrigin.BACKFILL);
    }

    @Test
    void aHumanAskingByHandIsASelfSelectedSample() {
        assertThat(SignalOrigins.observationOriginOf(DiscoveredVia.MANUAL)).isEqualTo(ObservationOrigin.MANUAL);
    }

    /** Reconciliation never triggers a review on its own, so a row it leaves is only ever acted on later,
     * through the event vocabulary — LIVE is the honest reading. */
    @Test
    void whatWeLearnedFromTheProviderIsALiveMeasurement() {
        assertThat(SignalOrigins.observationOriginOf(DiscoveredVia.EVENT)).isEqualTo(ObservationOrigin.LIVE);
        assertThat(SignalOrigins.observationOriginOf(DiscoveredVia.SYNC)).isEqualTo(ObservationOrigin.LIVE);
    }

    /**
     * A sweep is filed as LIVE, not BACKFILL: its bounded window makes its corpus "what happened recently",
     * the same population the event path measures, and BACKFILL rows are invisible to the developer read
     * model.
     */
    @Test
    void aScheduledSweepMeasuresTheSamePopulationTheEventPathMeasures() {
        assertThat(SignalOrigins.observationOriginOf(DiscoveredVia.SWEEP)).isEqualTo(ObservationOrigin.LIVE);
    }

    /** A new discovery mode must state its population rather than inherit one by accident. */
    @ParameterizedTest
    @EnumSource(DiscoveredVia.class)
    void everyDiscoveryModeNamesAPopulation(DiscoveredVia discoveredVia) {
        assertThat(SignalOrigins.observationOriginOf(discoveredVia)).isNotNull();
    }
}
