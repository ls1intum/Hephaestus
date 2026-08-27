package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRepricer.Outcome;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Clears the month's unpriced spend once the operator supplies the missing price.
 *
 * <p><b>The hole this fills.</b> One UNPRICED event funded from a capped purse makes that purse's month
 * UNVERIFIABLE, and an UNVERIFIABLE purse is blocked exactly as hard as an exhausted one — every agent
 * job in the workspace is held. There was no way back. Adding the price to the catalogue did not help,
 * because the ledger row was already written; the three remaining options were to remove the cap, to
 * UPDATE the ledger by hand, or to wait for the first of the month, and none of them is an operation the
 * product offers. This pass makes the obvious action the working one: add the model's price in the admin
 * console, and within a quarter of an hour the block lifts by itself.
 *
 * <p><b>Scope is the current month only.</b> That is the only window any cap reads, so it is the only
 * window where an unpriced row costs anybody anything. Repricing older months would rewrite settled
 * accounts to no operational end.
 *
 * <p>What it cannot fix is reported rather than hidden: {@code llm.usage.reprice.unresolved} counts rows
 * whose model this instance can no longer identify at all, which no catalogue edit will reach. Those are
 * the only case where removing the cap is still the answer, and the counter is what tells an operator
 * they are in it.
 */
@ConditionalOnServerRole
@Component
@WorkspaceAgnostic("Repricing the instance's ledger is inherently cross-workspace; touches spend metadata only")
public class LlmUsageRepricingSweeper {

    private static final Logger log = LoggerFactory.getLogger(LlmUsageRepricingSweeper.class);

    /**
     * Bounds one pass. Generous, because the case this exists for is a catalogue gap that left a whole
     * month's rows unpriced at once, and clearing that in one pass is the point — but still bounded, so a
     * pathological month cannot hold the scheduler lock indefinitely.
     */
    static final int MAX_ROWS_PER_PASS = 5_000;

    private final LlmUsageEventRepository usageRepository;
    private final LlmUsageRepricer repricer;
    private final MeterRegistry meterRegistry;

    public LlmUsageRepricingSweeper(
            LlmUsageEventRepository usageRepository, LlmUsageRepricer repricer, MeterRegistry meterRegistry) {
        this.usageRepository = usageRepository;
        this.repricer = repricer;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(cron = "0 */15 * * * *")
    @SchedulerLock(name = "llm-usage-reprice", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void reprice() {
        repriceNow(Instant.now());
    }

    /**
     * Reprice everything unpriced in {@code now}'s calendar month. Exposed so tests can put a fixture in
     * a chosen month rather than depend on today's date.
     *
     * @return how many rows each outcome applied to
     */
    public Map<Outcome, Integer> repriceNow(Instant now) {
        LlmBudgetService.MonthWindow window =
                LlmBudgetService.MonthWindow.of(YearMonth.from(now.atOffset(ZoneOffset.UTC)));
        List<UnpricedLedgerRow> unpriced =
                usageRepository.findUnpricedInWindow(window.from(), window.to(), PageRequest.of(0, MAX_ROWS_PER_PASS));
        Map<Outcome, Integer> tally = new EnumMap<>(Outcome.class);
        for (Outcome outcome : Outcome.values()) {
            tally.put(outcome, 0);
        }
        if (unpriced.isEmpty()) {
            return tally;
        }
        for (UnpricedLedgerRow row : unpriced) {
            Outcome outcome;
            try {
                outcome = repricer.reprice(row);
            } catch (RuntimeException e) {
                // One row's catalogue lookup failing must not abandon the rest of the month's backlog:
                // the whole purpose of this pass is to unblock a workspace, and a partial unblock is
                // still no unblock.
                log.warn("llm.usage.reprice: event {} could not be repriced: {}", row.id(), e.toString());
                meterRegistry.counter("llm.usage.reprice.failure").increment();
                continue;
            }
            tally.merge(outcome, 1, Integer::sum);
        }
        meterRegistry.counter("llm.usage.reprice.unresolved").increment(tally.getOrDefault(Outcome.UNIDENTIFIABLE, 0));
        log.info(
                "llm.usage.reprice: {} unpriced event(s) this month; {} repriced, {} still awaiting a catalogue price, "
                        + "{} name a model this instance cannot identify",
                unpriced.size(),
                tally.get(Outcome.REPRICED),
                tally.get(Outcome.STILL_UNPRICEABLE),
                tally.get(Outcome.UNIDENTIFIABLE));
        return tally;
    }
}
