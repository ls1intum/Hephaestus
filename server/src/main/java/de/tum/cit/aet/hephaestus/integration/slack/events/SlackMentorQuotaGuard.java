package de.tum.cit.aet.hephaestus.integration.slack.events;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMentorDailyBudgetRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Per-user turn/day cap plus a fleet-wide daily LLM-budget cap for the Slack mentor DM path. The existing
 * {@code core.auth.ratelimit} filter is HTTP-auth-scoped and never sees this flow (inbound Slack events ride the
 * unauthenticated worker-hub chain), so a runaway or abusive DM sender would otherwise be uncapped. This guard is
 * consulted in {@link SlackMentorService#handleDm} — deliberately not in {@code MentorTurnRunner}, which would also
 * throttle the unrelated HTTP mentor {@code start()} path.
 *
 * <p><strong>Two caps, two grains of accuracy.</strong>
 * <ul>
 *   <li><b>Per-user/day cap — in-memory, per replica.</b> A coarse per-sender valve; it tolerates drift under
 *       multiple replicas (each pod enforces its own share) because the fleet budget below is the real spend cap.</li>
 *   <li><b>Fleet daily-budget cap — shared Postgres counter.</b> This is the LLM-cost cap, so it must be
 *       <em>one</em> budget across the whole fleet. A per-replica in-memory counter would make the effective cap
 *       N× the budget with N replicas. It is a single {@code slack_mentor_daily_budget} row per UTC day, advanced
 *       atomically via {@link SlackMentorDailyBudgetRepository#tryConsume} ({@code INSERT … ON CONFLICT DO UPDATE …
 *       WHERE used < :budget}), so concurrent replicas serialize on the row and the combined draw-down is exactly
 *       the budget regardless of replica count.</li>
 * </ul>
 *
 * <p>Both caps are day-bucketed on the UTC day: the in-memory per-user map resets at UTC midnight, and the shared
 * counter naturally rolls to a fresh row keyed on the new {@link LocalDate}. Over-cap returns a {@link Decision} the
 * caller turns into a friendly Slack reply — never an exception.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackMentorQuotaGuard {

    /** Outcome of a quota check. */
    public enum Decision {
        /** Under both caps — the turn may run (and has been counted). */
        ALLOWED,
        /** This user has used their per-day turn allowance. */
        USER_CAP_EXCEEDED,
        /** The fleet-wide daily mentor budget is exhausted. */
        DAILY_BUDGET_EXCEEDED,
    }

    private final SlackMentorDailyBudgetRepository dailyBudgetRepository;
    private final int turnsPerUserPerDay;
    private final int dailyBudget;
    private final Clock clock;

    private final Object lock = new Object();
    private final Map<String, Integer> perUserCount = new HashMap<>();
    private LocalDate windowDay;

    @Autowired
    public SlackMentorQuotaGuard(
        SlackMentorDailyBudgetRepository dailyBudgetRepository,
        @Value("${hephaestus.integration.slack.mentor.turns-per-user-per-day:50}") int turnsPerUserPerDay,
        @Value("${hephaestus.integration.slack.mentor.daily-budget:1000}") int dailyBudget
    ) {
        this(dailyBudgetRepository, turnsPerUserPerDay, dailyBudget, Clock.systemUTC());
    }

    /** Test seam: the shared-budget repo, the caps, and an injectable clock for deterministic day-roll assertions. */
    public SlackMentorQuotaGuard(
        SlackMentorDailyBudgetRepository dailyBudgetRepository,
        int turnsPerUserPerDay,
        int dailyBudget,
        Clock clock
    ) {
        this.dailyBudgetRepository = dailyBudgetRepository;
        this.turnsPerUserPerDay = turnsPerUserPerDay;
        this.dailyBudget = dailyBudget;
        this.clock = clock;
    }

    /**
     * Try to consume one mentor turn for {@code userKey}. On {@link Decision#ALLOWED} the turn is counted against
     * both the per-user and the fleet daily-budget tallies; the other decisions consume nothing.
     *
     * <p>Ordering matches the invariant that a user already at their own cap never draws down the shared budget: the
     * in-memory per-user cap is checked first (no DB touch on {@link Decision#USER_CAP_EXCEEDED}); only then is the
     * shared budget consumed. If the budget is exhausted the per-user tally is left untouched.
     */
    public Decision tryAcquire(String userKey) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));

        synchronized (lock) {
            rollDayIfNeeded(today);
            if (perUserCount.getOrDefault(userKey, 0) >= turnsPerUserPerDay) {
                return Decision.USER_CAP_EXCEEDED;
            }
        }

        // Fleet budget lives in shared state so N replicas enforce ONE budget. Consumes one unit iff strictly
        // under budget; the whole check-and-increment is a single atomic statement. Kept outside the monitor so a
        // slow DB round-trip never blocks unrelated senders' per-user checks.
        if (dailyBudgetRepository.tryConsume(today, dailyBudget) != 1) {
            return Decision.DAILY_BUDGET_EXCEEDED;
        }

        synchronized (lock) {
            rollDayIfNeeded(today);
            perUserCount.merge(userKey, 1, Integer::sum);
        }
        return Decision.ALLOWED;
    }

    private void rollDayIfNeeded(LocalDate today) {
        if (!today.equals(windowDay)) {
            windowDay = today;
            perUserCount.clear();
        }
    }
}
