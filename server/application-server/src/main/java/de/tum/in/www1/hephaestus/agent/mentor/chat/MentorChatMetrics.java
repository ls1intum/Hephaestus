package de.tum.in.www1.hephaestus.agent.mentor.chat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Micrometer instruments for the mentor chat turn pipeline.
 *
 * <p>The naming convention follows the rest of {@code agent/} ({@code agent.job.*},
 * {@code llm.proxy.*}): dot-case Java identifiers that Micrometer's Prometheus renderer
 * transforms to {@code mentor_turn_started_total} etc. Outcome tag values are kept to a fixed
 * enum so cardinality stays bounded.
 *
 * <p>Wired up in {@link MentorChatService}. Counters bracket the same code-region the audit
 * rubric called out ({@code mentor.turn.started} at lock acquired; {@code mentor.turn.completed}
 * at every terminal branch with a single {@link Outcome} label).
 */
@Component
@ConditionalOnProperty(name = "hephaestus.mentor.enabled", havingValue = "true")
public class MentorChatMetrics {

    /**
     * Tag value enum kept small; reviewers can scan the table to see every terminal branch.
     *
     * <p>The two conflict outcomes are distinct on purpose: {@link #IN_FLIGHT_CONFLICT_LOCAL}
     * fires when the in-JVM {@link MentorTurnLock} rejects a same-JVM double-submit;
     * {@link #IN_FLIGHT_CONFLICT_DB} fires when the durable partial-unique index catches a
     * cross-replica race (until #1077 sticky-affinity lands). Operationally, the DB-side
     * outcome firing in prod is the signal that says "the JVM lock leaked through replica
     * boundaries"; collapsing them into one label hides whether the durable backstop ever
     * fires, which was the whole point of having both layers.
     */
    public enum Outcome {
        SUCCESS("success"),
        CLIENT_DISCONNECT("client_disconnect"),
        POISONED("poisoned"),
        TIMEOUT("timeout"),
        IN_FLIGHT_CONFLICT_LOCAL("in_flight_conflict_local"),
        IN_FLIGHT_CONFLICT_DB("in_flight_conflict_db"),
        REJECTED("rejected"),
        ERROR("error");

        private final String tag;

        Outcome(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    private final MeterRegistry registry;
    private final Counter started;
    private final Map<Outcome, Counter> completedByOutcome;
    private final Timer duration;
    private final DistributionSummary costUsd;

    public MentorChatMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.started = Counter.builder("mentor.turn.started")
            .description(
                "Mentor chat turns observed at executor submit — increments on every dispatch, regardless of lock outcome."
            )
            .register(registry);
        this.duration = Timer.builder("mentor.turn.duration")
            .description("Mentor chat turn wall-clock duration including sandbox attach + Pi RPC.")
            .publishPercentileHistogram()
            .register(registry);
        this.costUsd = DistributionSummary.builder("mentor.turn.cost.usd")
            .description("Per-turn LLM cost in USD (skipped for turns where cost is unresolvable).")
            .baseUnit("USD")
            .register(registry);

        // Pre-register one Counter per outcome at construction. Avoids per-call
        // Counter.builder + Tags allocation on the hot path (Micrometer perf wiki).
        this.completedByOutcome = new EnumMap<>(Outcome.class);
        for (Outcome o : Outcome.values()) {
            completedByOutcome.put(
                o,
                Counter.builder("mentor.turn.completed")
                    .description("Mentor chat turns terminated, tagged by outcome.")
                    .tag("outcome", o.tag())
                    .register(registry)
            );
        }
    }

    public void recordStarted() {
        started.increment();
    }

    public void recordCompleted(Outcome outcome) {
        completedByOutcome.get(outcome).increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void stopTimer(Timer.Sample sample) {
        sample.stop(duration);
    }

    public void recordCostUsd(double usd) {
        if (Double.isFinite(usd) && usd >= 0d) {
            costUsd.record(usd);
        }
    }
}
