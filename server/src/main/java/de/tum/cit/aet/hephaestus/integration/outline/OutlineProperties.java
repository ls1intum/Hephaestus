package de.tum.cit.aet.hephaestus.integration.outline;

import static java.time.temporal.ChronoUnit.HOURS;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;

/**
 * Configuration for the Outline document sync and its local mirror.
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * hephaestus:
 *   integration:
 *     outline:
 *       enabled: true
 *       sync:
 *         cron: "0 0 0/6 * * *"
 *         export-budget: 500
 *         catch-up-delay: PT5M
 *       cache:
 *         max-size-mb: 200
 *       staleness: 30d
 * }</pre>
 *
 * @param sync      scheduling and pacing of the reconcile passes
 * @param cache     bound on the per-workspace mirrored-body footprint
 * @param staleness how long a document that has vanished upstream is kept as a tombstone before its row is
 *                  dropped entirely (defence-in-depth ceiling on stale rows; default 30 days)
 */
@ConfigurationProperties(prefix = "hephaestus.integration.outline")
public record OutlineProperties(
    @DefaultValue Sync sync,
    @DefaultValue Cache cache,
    @DurationUnit(HOURS) @DefaultValue("720h") Duration staleness
) {
    /**
     * The {@code cron} and {@code catch-up-delay} yml keys are read straight off the Environment by the
     * {@code @Scheduled} SpEL in {@code OutlineDocumentSyncScheduler} (matching the Slack sibling), so they
     * are deliberately not bound here — a record component would be dead config.
     *
     * @param exportBudget max document exports one workspace pass may spend (default 500). Bounds the
     *                     cost of a huge corpus's first sync; a collection whose pass ran out of budget
     *                     stays {@code PENDING} and the catch-up tick resumes it next cycle
     */
    public record Sync(@DefaultValue("500") int exportBudget) {}

    /**
     * @param maxSizeMb per-workspace cap on the total size of mirrored Markdown bodies; when exceeded, the
     *                  least-recently-materialized bodies are evicted (nulled) until the mirror is back under
     *                  the cap. The row survives as a directory marker (default 200 MB)
     */
    public record Cache(@DefaultValue("200") int maxSizeMb) {}
}
