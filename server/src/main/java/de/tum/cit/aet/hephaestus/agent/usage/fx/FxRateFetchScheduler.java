package de.tum.cit.aet.hephaestus.agent.usage.fx;

import de.tum.cit.aet.hephaestus.agent.usage.fx.EcbFxRateClient.EcbDailyRate;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps {@code fx_rate} current: one ECB fetch a day, plus a catch-up fetch at boot when nothing
 * usable is stored.
 *
 * <p>{@link ConditionalOnServerRole} keeps this bean — and with it the only outbound dependency this
 * feature has — off the worker and webhook tiers, which would otherwise crash-loop on an egress
 * dependency they have no business having. {@link SchedulerLock} then stops two server replicas from
 * both fetching.
 */
@ConditionalOnServerRole
@Component
@WorkspaceAgnostic("Public reference rates are instance-wide reference data, not tenant data")
public class FxRateFetchScheduler {

    private static final Logger log = LoggerFactory.getLogger(FxRateFetchScheduler.class);

    private final EcbFxRateClient client;
    private final FxRateRepository fxRateRepository;
    private final FxRateLookup fxRateLookup;
    private final Clock clock;

    public FxRateFetchScheduler(
        EcbFxRateClient client,
        FxRateRepository fxRateRepository,
        FxRateLookup fxRateLookup,
        Clock clock
    ) {
        this.client = client;
        this.fxRateRepository = fxRateRepository;
        this.fxRateLookup = fxRateLookup;
        this.clock = clock;
    }

    /**
     * The ECB publishes at around 16:00 CET on TARGET working days; 16:30 leaves headroom for a late
     * publication without waiting a whole day. A TARGET holiday produces a no-op fetch, well inside the
     * staleness window.
     */
    @Scheduled(cron = "0 30 16 * * MON-FRI", zone = "Europe/Berlin")
    @SchedulerLock(name = "fx-rate-fetch", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void fetchDaily() {
        fetchIfEnabled("scheduled");
    }

    /**
     * Covers the first start after an operator sets {@code hephaestus.llm.display-currency}, and an
     * instance returning from an outage long enough to have gone stale. Makes no outbound request at
     * all under the default configuration.
     *
     * <p><b>Cannot fail the boot.</b> An exception thrown from an {@link ApplicationReadyEvent}
     * listener aborts {@code SpringApplication.run}, so without this guard an unreachable database or
     * an ECB outage at the wrong moment would crash-loop the server — over a number that is only ever
     * shown beside the USD one. The next scheduled fetch retries; until then the UI simply shows USD.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void fetchOnStartupIfMissing() {
        try {
            if (!fxRateLookup.isEnabled() || fxRateLookup.latest().isPresent()) {
                return;
            }
            fetchIfEnabled("bootstrap");
        } catch (RuntimeException e) {
            log.warn("fx: bootstrap rate fetch failed — display currency stays unavailable until the daily fetch", e);
        }
    }

    private void fetchIfEnabled(String trigger) {
        if (!fxRateLookup.isEnabled()) {
            return;
        }
        Optional<EcbDailyRate> fetched = client.fetchLatestUsdRate();
        if (fetched.isEmpty()) {
            // Already warned by the client; the stored rate stays untouched.
            return;
        }
        store(fetched.get(), trigger);
    }

    /**
     * Deliberately untransacted: each statement is atomic on its own and the daily unique index is the
     * real arbiter of a two-replica race, so an enclosing transaction would only hold a connection open
     * across an HTTP fetch.
     */
    void store(EcbDailyRate rate, String trigger) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (rate.date().isAfter(today.plusDays(1))) {
            // A date from the future means we are not reading what we think we are reading.
            log.warn("fx: ignoring ECB rate dated {} — later than today ({})", rate.date(), today);
            return;
        }
        Optional<FxRate> existing = fxRateRepository.findByRateDate(rate.date());
        FxRate row = existing.orElseGet(FxRate::new);
        boolean unchanged = existing.isPresent() && existing.get().getUsdPerEur().compareTo(rate.usdPerEur()) == 0;
        row.setRateDate(rate.date());
        row.setUsdPerEur(rate.usdPerEur());
        row.setFetchedAt(Instant.now(clock));
        try {
            fxRateRepository.save(row);
        } catch (DataIntegrityViolationException e) {
            // Another replica inserted the same day between our read and our write; its row is
            // identical to ours, so there is nothing to reconcile.
            log.debug("fx: concurrent insert for {} — keeping the row that won", rate.date());
            return;
        }
        if (!unchanged) {
            log.info("fx: stored ECB rate {} USD per EUR for {} (trigger={})", rate.usdPerEur(), rate.date(), trigger);
        }
    }
}
