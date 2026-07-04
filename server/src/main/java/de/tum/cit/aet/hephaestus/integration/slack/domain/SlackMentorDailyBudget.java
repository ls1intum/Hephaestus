package de.tum.cit.aet.hephaestus.integration.slack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Shared, fleet-wide daily draw-down counter for the Slack mentor DM path. One row per UTC day holds the number of
 * mentor turns consumed against the fleet budget so far. It exists because the mentor's daily LLM-spend cap must be
 * <em>one</em> budget across every replica: an in-memory counter per pod multiplies the real budget by the replica
 * count (N replicas ⇒ N× the intended spend). The counter is advanced atomically via the repository's
 * {@code INSERT … ON CONFLICT (day) DO UPDATE … WHERE used &lt; :budget}, so concurrent replicas serialize on the
 * row and exactly one combined budget is enforced.
 *
 * <p><strong>Workspace-independent by construction.</strong> This is a fleet-level operational budget, not tenant
 * data, so the table carries no {@code workspace_id}, is listed in {@code WorkspaceScopedTables.GLOBAL_TABLES}, and
 * its repository is {@code @WorkspaceAgnostic}. It holds no workspace content, so the Slack workspace-purge adapter
 * deliberately does not cascade it.
 *
 * <p>Writes go through the repository's native upsert; the entity exists so Hibernate's schema check and the
 * tenancy metamodel walk both see the table. Old days' rows are harmless residue (never read after their day) — a
 * generic housekeeping sweep can prune them, but none is required for correctness.
 */
@Entity
@Table(name = "slack_mentor_daily_budget")
@Getter
@Setter
@NoArgsConstructor
public class SlackMentorDailyBudget {

    /** The UTC day this counter bucket belongs to. */
    @Id
    @Column(name = "day", nullable = false)
    private LocalDate day;

    /** Mentor turns consumed against the fleet budget on {@link #day} so far. */
    @Column(name = "used", nullable = false)
    private int used;
}
