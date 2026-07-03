package de.tum.cit.aet.hephaestus.integration.slack.events;

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
 * Per-user turn/day cap plus a fleet-wide daily LLM-budget cap for the Slack mentor DM path (S9). The existing
 * {@code core.auth.ratelimit} filter is HTTP-auth-scoped and never sees this flow (inbound Slack events ride the
 * unauthenticated worker-hub chain), so a runaway or abusive DM sender would otherwise be uncapped. This guard is
 * consulted in {@link SlackMentorService#handleDm} — deliberately not in {@code MentorTurnRunner}, which would also
 * throttle the unrelated HTTP mentor {@code start()} path.
 *
 * <p>Both caps are day-bucketed and reset at UTC midnight. Counting is in-memory per replica: it is a coarse safety
 * valve, not an accounting ledger, so approximate fleet-wide counting under multiple replicas is acceptable (each
 * replica enforces its own share of the budget). Over-cap returns a {@link Decision} the caller turns into a
 * friendly Slack reply — never an exception.
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

    private final int turnsPerUserPerDay;
    private final int dailyBudget;
    private final Clock clock;

    private final Object lock = new Object();
    private final Map<String, Integer> perUserCount = new HashMap<>();
    private LocalDate windowDay;
    private int budgetUsed;

    @Autowired
    public SlackMentorQuotaGuard(
        @Value("${hephaestus.integration.slack.mentor.turns-per-user-per-day:50}") int turnsPerUserPerDay,
        @Value("${hephaestus.integration.slack.mentor.daily-budget:1000}") int dailyBudget
    ) {
        this(turnsPerUserPerDay, dailyBudget, Clock.systemUTC());
    }

    /** Test seam: caps + an injectable clock for deterministic day-roll assertions. */
    public SlackMentorQuotaGuard(int turnsPerUserPerDay, int dailyBudget, Clock clock) {
        this.turnsPerUserPerDay = turnsPerUserPerDay;
        this.dailyBudget = dailyBudget;
        this.clock = clock;
    }

    /**
     * Try to consume one mentor turn for {@code userKey}. On {@link Decision#ALLOWED} the turn is counted against
     * both the per-user and the daily-budget tallies; the other decisions consume nothing.
     */
    public Decision tryAcquire(String userKey) {
        synchronized (lock) {
            LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
            if (!today.equals(windowDay)) {
                windowDay = today;
                perUserCount.clear();
                budgetUsed = 0;
            }
            int used = perUserCount.getOrDefault(userKey, 0);
            if (used >= turnsPerUserPerDay) {
                return Decision.USER_CAP_EXCEEDED;
            }
            if (budgetUsed >= dailyBudget) {
                return Decision.DAILY_BUDGET_EXCEEDED;
            }
            perUserCount.put(userKey, used + 1);
            budgetUsed++;
            return Decision.ALLOWED;
        }
    }
}
