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
 * <p><b>Server role only.</b> {@link ConditionalOnServerRole} keeps this bean — and with it the only
 * outbound dependency this feature has — off the worker and webhook tiers. Those pods deploy with
 * {@code hephaestus.runtime.server.enabled=false}; an ungated bean here would hand them an egress
 * dependency they have no business having (and previously crash-looped them). {@link SchedulerLock}
 * then stops two server replicas from both fetching.
 *
 * <p><b>Nothing here can fail a tick.</b> {@link EcbFxRateClient} never throws, an unchanged rate is
 * a no-op, and a lost race on the daily unique row is swallowed. The worst outcome of a broken fetch
 * is that rates go stale — at which point {@link FxRateLookup} stops converting altogether rather
 * than showing a drifting number.
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
     * The ECB publishes its daily reference rates at around 16:00 CET on TARGET working days; 16:30
     * leaves headroom for a late publication without waiting a whole day. Weekends are skipped
     * because nothing is published then — TARGET holidays simply produce a no-op fetch (the document
     * still carries the previous working day) well inside the staleness window.
     */
    @Scheduled(cron = "0 30 16 * * MON-FRI", zone = "Europe/Berlin")
    @SchedulerLock(name = "fx-rate-fetch", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void fetchDaily() {
        fetchIfEnabled("scheduled");
    }

    /**
     * Catch-up fetch at boot when no usable rate is stored — the first start after an operator sets
     * {@code hephaestus.llm.display-currency}, and equally an instance coming back from an outage
     * long enough to have gone stale. Skipped entirely when the display currency is unset, so the
     * default configuration makes no outbound request at all.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void fetchOnStartupIfMissing() {
        if (!fxRateLookup.isEnabled() || fxRateLookup.latest().isPresent()) {
            return;
        }
        fetchIfEnabled("bootstrap");
    }

    private void fetchIfEnabled(String trigger) {
        if (!fxRateLookup.isEnabled()) {
            return;
        }
        Optional<EcbDailyRate> fetched = client.fetchLatestUsdRate();
        if (fetched.isEmpty()) {
            // Already warned by the client; the previously stored rate stays untouched.
            return;
        }
        store(fetched.get(), trigger);
    }

    /**
     * Deliberately untransacted: the read and the write are two independent statements, each already
     * atomic on its own, and the daily unique index is the real arbiter of a two-replica race. An
     * enclosing transaction here would only widen the window while an HTTP fetch is in flight.
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
            // Another replica inserted the same day between our read and our write. Its row is
            // identical to ours, so there is nothing to reconcile.
            log.debug("fx: concurrent insert for {} — keeping the row that won", rate.date());
            return;
        }
        if (!unchanged) {
            log.info("fx: stored ECB rate {} USD per EUR for {} (trigger={})", rate.usdPerEur(), rate.date(), trigger);
        }
    }
}
