package de.tum.cit.aet.hephaestus.integration.slack.domain;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared-state access to the fleet-wide {@link SlackMentorDailyBudget} counter. The whole table is
 * workspace-independent (a fleet operational budget, resolved before/without any workspace), so every query here is
 * {@link WorkspaceAgnostic} — there is no {@code workspace_id} to predicate on, and the table is in
 * {@code WorkspaceScopedTables.GLOBAL_TABLES}.
 */
@WorkspaceAgnostic("Fleet-wide Slack mentor LLM-spend budget; a single global counter, not workspace-scoped")
public interface SlackMentorDailyBudgetRepository extends JpaRepository<SlackMentorDailyBudget, LocalDate> {
    /**
     * Atomically consume one unit of the fleet daily budget for {@code day}, iff the current usage is strictly below
     * {@code budget}. Emits a single {@code INSERT … ON CONFLICT (day) DO UPDATE … WHERE used < :budget}:
     *
     * <ul>
     *   <li>Row absent and {@code budget > 0} → inserts {@code used = 1} (this turn is the day's first) → returns 1.</li>
     *   <li>Row absent and {@code budget <= 0} → the source row is filtered out, nothing is inserted → returns 0.</li>
     *   <li>Row present and {@code used < budget} → increments {@code used} by one → returns 1.</li>
     *   <li>Row present and {@code used >= budget} → the conflict's {@code WHERE} is false, no row is written → returns 0.</li>
     * </ul>
     *
     * <p>Because the whole check-and-increment is one statement, concurrent replicas serialize on the conflicting
     * row (Postgres re-evaluates the {@code WHERE} against the committed row version), so the combined draw-down can
     * never exceed {@code budget} regardless of replica count.
     *
     * @return {@code 1} when a unit was consumed (caller is under budget), {@code 0} when the budget is exhausted
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO slack_mentor_daily_budget (day, used)
        SELECT :day, 1 WHERE :budget > 0
        ON CONFLICT (day) DO UPDATE SET used = slack_mentor_daily_budget.used + 1
        WHERE slack_mentor_daily_budget.used < :budget
        """,
        nativeQuery = true
    )
    int tryConsume(@Param("day") LocalDate day, @Param("budget") int budget);
}
