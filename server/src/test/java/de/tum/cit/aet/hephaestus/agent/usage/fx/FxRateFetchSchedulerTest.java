package de.tum.cit.aet.hephaestus.agent.usage.fx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.usage.fx.EcbFxRateClient.EcbDailyRate;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The fetch side: what lands in {@code fx_rate}, what deliberately does not, and — the load-bearing
 * one — that this bean simply does not exist off the server role. The worker and webhook tiers must
 * not acquire an outbound dependency on ecb.europa.eu; an ungated bean in this area has crash-looped
 * them before.
 */
class FxRateFetchSchedulerTest extends BaseUnitTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 24);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    private static final EcbDailyRate FETCHED = new EcbDailyRate(TODAY, new BigDecimal("1.1377"));

    @Mock
    private EcbFxRateClient client;

    @Mock
    private FxRateRepository repository;

    @Mock
    private FxRateLookup lookup;

    private FxRateFetchScheduler scheduler() {
        return new FxRateFetchScheduler(client, repository, lookup, CLOCK);
    }

    // DAILY FETCH

    @Test
    @DisplayName("should store a freshly published rate")
    void shouldStoreRateWhenFetchSucceeds() {
        when(lookup.isEnabled()).thenReturn(true);
        when(client.fetchLatestUsdRate()).thenReturn(Optional.of(FETCHED));
        when(repository.findByRateDate(TODAY)).thenReturn(Optional.empty());

        scheduler().fetchDaily();

        ArgumentCaptor<FxRate> saved = ArgumentCaptor.forClass(FxRate.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getRateDate()).isEqualTo(TODAY);
        // Stored in the ECB's own direction — inverting on write would bake a rounding step into history.
        assertThat(saved.getValue().getUsdPerEur()).isEqualByComparingTo("1.1377");
        assertThat(saved.getValue().getFetchedAt()).isEqualTo(Instant.now(CLOCK));
    }

    @Test
    @DisplayName("should overwrite the same day's row when the rate is republished")
    void shouldUpdateExistingRowWhenSameDayFetchedAgain() {
        FxRate existing = new FxRate();
        existing.setRateDate(TODAY);
        existing.setUsdPerEur(new BigDecimal("1.1000"));
        existing.setFetchedAt(Instant.EPOCH);
        when(lookup.isEnabled()).thenReturn(true);
        when(client.fetchLatestUsdRate()).thenReturn(Optional.of(FETCHED));
        when(repository.findByRateDate(TODAY)).thenReturn(Optional.of(existing));

        scheduler().fetchDaily();

        verify(repository).save(existing);
        assertThat(existing.getUsdPerEur()).isEqualByComparingTo("1.1377");
    }

    @Test
    @DisplayName("should leave the table untouched when the fetch fails")
    void shouldNotWriteWhenFetchFails() {
        when(lookup.isEnabled()).thenReturn(true);
        when(client.fetchLatestUsdRate()).thenReturn(Optional.empty());

        scheduler().fetchDaily();

        // The previously stored rate survives; staleness — not a wrong number — is the failure mode.
        verify(repository, never()).save(any());
        verify(repository, never()).findByRateDate(any());
    }

    @Test
    @DisplayName("should make no outbound request when no display currency is configured")
    void shouldNotFetchWhenDisplayCurrencyUnset() {
        when(lookup.isEnabled()).thenReturn(false);

        scheduler().fetchDaily();

        verifyNoInteractions(client);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("should ignore a rate dated in the future")
    void shouldIgnoreRateWhenDatedAfterToday() {
        when(lookup.isEnabled()).thenReturn(true);
        when(client.fetchLatestUsdRate()).thenReturn(
            Optional.of(new EcbDailyRate(TODAY.plusDays(5), new BigDecimal("1.1377")))
        );

        scheduler().fetchDaily();

        verify(repository, never()).save(any());
    }

    // BOOTSTRAP

    @Test
    @DisplayName("should fetch at startup when nothing usable is stored")
    void shouldFetchOnStartupWhenNoRateStored() {
        when(lookup.isEnabled()).thenReturn(true);
        when(lookup.latest()).thenReturn(Optional.empty());
        when(client.fetchLatestUsdRate()).thenReturn(Optional.of(FETCHED));
        when(repository.findByRateDate(TODAY)).thenReturn(Optional.empty());

        scheduler().fetchOnStartupIfMissing();

        verify(repository).save(any());
    }

    @Test
    @DisplayName("should not fetch at startup when a usable rate is already stored")
    void shouldNotFetchOnStartupWhenRateAlreadyStored() {
        when(lookup.isEnabled()).thenReturn(true);
        when(lookup.latest()).thenReturn(
            Optional.of(FxRateInfoDTO.fromEcbRate("EUR", new BigDecimal("1.1377"), TODAY))
        );

        scheduler().fetchOnStartupIfMissing();

        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("should not fetch at startup when no display currency is configured")
    void shouldNotFetchOnStartupWhenDisplayCurrencyUnset() {
        when(lookup.isEnabled()).thenReturn(false);

        scheduler().fetchOnStartupIfMissing();

        verifyNoInteractions(client);
        verifyNoInteractions(repository);
    }

    // ROLE GATING

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
            .withBean(EcbFxRateClient.class, () -> mock(EcbFxRateClient.class))
            .withBean(FxRateRepository.class, () -> mock(FxRateRepository.class))
            .withBean(FxRateLookup.class, () -> mock(FxRateLookup.class))
            .withBean(Clock.class, () -> CLOCK)
            .withUserConfiguration(FxRateFetchScheduler.class);
    }

    @Test
    @DisplayName("should not register the fetch scheduler when the server role is off")
    void shouldOmitSchedulerBeanWhenServerRoleDisabled() {
        contextRunner()
            .withPropertyValues(RuntimeRole.SERVER_PROPERTY + "=false")
            .run(context -> assertThat(context).doesNotHaveBean(FxRateFetchScheduler.class));
    }

    @Test
    @DisplayName("should register the fetch scheduler on a default (single-JVM) deployment")
    void shouldRegisterSchedulerBeanWhenRolePropertyAbsent() {
        // matchIfMissing=true — zero env vars still boots a full monolith (ADR 0005).
        contextRunner().run(context -> assertThat(context).hasSingleBean(FxRateFetchScheduler.class));
    }
}
