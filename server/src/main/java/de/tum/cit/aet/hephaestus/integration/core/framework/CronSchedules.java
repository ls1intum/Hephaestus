package de.tum.cit.aet.hephaestus.integration.core.framework;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;

/** Cron helpers shared by the sync-state providers. */
public final class CronSchedules {

    private static final Logger log = LoggerFactory.getLogger(CronSchedules.class);

    private CronSchedules() {}

    /**
     * Next fire time of {@code cron} in the server's default zone — matching {@code @Scheduled(cron=...)}'s
     * un-zoned behavior. Returns {@code null} when the expression is unparseable or has no future run. A
     * parse failure is logged at debug (this is recomputed on every overview load, so a louder level would
     * spam) rather than swallowed silently, keeping one consistent policy across all providers.
     */
    @Nullable
    public static Instant nextRun(String cron) {
        try {
            LocalDateTime next = CronExpression.parse(cron).next(LocalDateTime.now());
            return next == null ? null : next.atZone(ZoneId.systemDefault()).toInstant();
        } catch (IllegalArgumentException e) {
            log.debug("Unparseable sync cron '{}': {}", cron, e.getMessage());
            return null;
        }
    }
}
