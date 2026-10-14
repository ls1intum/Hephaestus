package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.metrics.AgentMetrics;
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
 * Retries missing prices for verified usage in the current UTC month.
 *
 * <p>The repository owns candidate eligibility, and the per-row repricer repeats the stored guards
 * atomically. Unknown or unverifiable measurements cannot be repaired by adding rates. Resolving a
 * price clears a capped purse's pause only when no other unpriced usage remains.
 *
 * <p>Older months retain their stored amounts. Outcome counters describe selected candidates, not
 * unknown or unverifiable records excluded from the work list.
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
     * Reprice eligible verified usage in {@code now}'s calendar month. Exposed so tests can put a fixture in
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
                meterRegistry.counter(AgentMetrics.LLM_USAGE_REPRICE_FAILURE).increment();
                continue;
            }
            tally.merge(outcome, 1, Integer::sum);
        }
        meterRegistry
                .counter(AgentMetrics.LLM_USAGE_REPRICE_UNRESOLVED)
                .increment(tally.getOrDefault(Outcome.UNIDENTIFIABLE, 0));
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
