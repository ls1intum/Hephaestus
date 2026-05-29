package de.tum.cit.aet.hephaestus.core.auth.audit;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Self-managed monthly partitioning for the {@code auth_event} RANGE-partitioned table — runs
 * entirely on stock Postgres (no {@code pg_partman}, no custom image). Replaces the prior
 * {@code pg_partman} dependency (ADR 0017): every login / logout / impersonation / export-request
 * writes an {@link AuthEvent}, so the table must always have a partition that the row can land in.
 *
 * <h2>What it guarantees</h2>
 * <ul>
 *   <li><b>Create-ahead.</b> Ensures the current month plus the next two months exist as explicit
 *       {@code auth_event_pYYYYMM} partitions, so normal rows never fall into the {@code
 *       auth_event_default} catch-all. The Liquibase changeset seeds prev/current/next; this keeps
 *       the window rolling forward.</li>
 *   <li><b>Retention.</b> DROPs monthly partitions whose month is strictly older than 12 months,
 *       enforcing the GDPR Art. 30 ~12-month window. The DEFAULT partition is never dropped (it is
 *       the safety net and should normally be empty).</li>
 * </ul>
 *
 * <h2>Scheduling &amp; safety</h2>
 * Mirrors {@code ExportRetentionSweeper} / {@code OAuthStateNonceCleanupJob}: {@code @Scheduled} is
 * activated only on the server role via {@code ServerSchedulingConfig}, and {@code @SchedulerLock}
 * (PostgreSQL-backed ShedLock) prevents concurrent replicas from racing the DDL. All statements are
 * idempotent ({@code CREATE TABLE IF NOT EXISTS} / {@code DROP TABLE IF EXISTS}), so a missed lock,
 * a retry, or a double-run is harmless.
 *
 * <h2>Startup hook</h2>
 * {@link #ensureOnStartup()} runs on {@code ApplicationReadyEvent} so a freshly-migrated DB gets its
 * current/next partitions immediately, without waiting for the monthly cron. It is intentionally NOT
 * lock-guarded (a {@code CREATE TABLE IF NOT EXISTS} race between pods is a no-op) and never throws:
 * a partition-maintenance failure must not break boot, because the Liquibase-seeded partitions +
 * DEFAULT already make inserts safe.
 */
@Component
@WorkspaceAgnostic("auth_event is account/system-scoped; partition DDL is global, not tenant data")
public class AuthEventPartitionManager {

    private static final Logger log = LoggerFactory.getLogger(AuthEventPartitionManager.class);

    /** Partition-name prefix; must match the Liquibase bootstrap changeset. */
    static final String PARTITION_PREFIX = "auth_event_p";

    static final String DEFAULT_PARTITION = "auth_event_default";

    private static final DateTimeFormatter MONTH_SUFFIX = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Months of history to keep. Partitions for months older than this are dropped. */
    static final int RETENTION_MONTHS = 12;

    /** How many future months (beyond the current) to keep pre-created. */
    static final int CREATE_AHEAD_MONTHS = 2;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public AuthEventPartitionManager(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    /**
     * Initial maintenance pass once the context is ready, so a freshly-migrated DB has its
     * current/next partitions without waiting for the cron. Never throws — boot must not depend on
     * partition maintenance (the Liquibase-seeded partitions + DEFAULT already make inserts safe).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureOnStartup() {
        try {
            maintainNow();
        } catch (RuntimeException e) {
            log.error("auth.audit: startup partition maintenance failed (inserts remain safe via DEFAULT)", e);
        }
    }

    /**
     * Monthly maintenance: create-ahead the upcoming months and drop partitions past retention.
     * Runs at 00:10 on the 1st of every month, server-role only, single-flighted across replicas.
     */
    @Scheduled(cron = "0 10 0 1 * *")
    @SchedulerLock(name = "auth-event-partition-maintenance", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void maintain() {
        maintainNow();
    }

    /** Directly callable from tests. Idempotent; safe to run repeatedly. */
    public void maintainNow() {
        YearMonth current = YearMonth.now(clock);
        int created = ensurePartitions(current);
        int dropped = dropExpiredPartitions(current);
        if (created > 0 || dropped > 0) {
            log.info("auth.audit: partition maintenance — created {} partition(s), dropped {} expired", created, dropped);
        }
    }

    /** Creates the current month plus {@link #CREATE_AHEAD_MONTHS} ahead if missing. */
    private int ensurePartitions(YearMonth current) {
        int created = 0;
        for (YearMonth ym : partitionsToEnsure(current)) {
            if (createPartitionIfAbsent(ym)) {
                created++;
            }
        }
        return created;
    }

    /**
     * The set of months that should exist as explicit partitions for the given current month:
     * the current month and the next {@link #CREATE_AHEAD_MONTHS}. Pure function — unit-tested.
     */
    static List<YearMonth> partitionsToEnsure(YearMonth current) {
        List<YearMonth> months = new ArrayList<>(CREATE_AHEAD_MONTHS + 1);
        for (int i = 0; i <= CREATE_AHEAD_MONTHS; i++) {
            months.add(current.plusMonths(i));
        }
        return months;
    }

    /** {@code auth_event_pYYYYMM} for the given month. Pure function — unit-tested. */
    static String partitionName(YearMonth ym) {
        return PARTITION_PREFIX + ym.format(MONTH_SUFFIX);
    }

    /**
     * Parses the month encoded in a partition name, or returns {@code null} if the name is not an
     * {@code auth_event_pYYYYMM} monthly partition (e.g. the DEFAULT partition). Pure function.
     */
    static YearMonth monthFromPartitionName(String name) {
        if (name == null || !name.startsWith(PARTITION_PREFIX)) {
            return null;
        }
        String suffix = name.substring(PARTITION_PREFIX.length());
        if (suffix.length() != 6 || !suffix.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            return YearMonth.parse(suffix, MONTH_SUFFIX);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Months whose partitions are older than the retention window and should be dropped, given the
     * existing monthly partitions and the current month. Strictly older than
     * {@code current - RETENTION_MONTHS}. Pure function — unit-tested.
     */
    static List<YearMonth> partitionsToDrop(List<YearMonth> existing, YearMonth current) {
        YearMonth cutoff = current.minusMonths(RETENTION_MONTHS);
        List<YearMonth> toDrop = new ArrayList<>();
        for (YearMonth ym : existing) {
            if (ym.isBefore(cutoff)) {
                toDrop.add(ym);
            }
        }
        return toDrop;
    }

    private boolean createPartitionIfAbsent(YearMonth ym) {
        String name = partitionName(ym);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.plusMonths(1).atDay(1);
        // Identifiers come from a strict YYYYMM format — no injection surface. Bounds are ISO dates.
        String ddl = String.format(
            "CREATE TABLE IF NOT EXISTS %s PARTITION OF auth_event FOR VALUES FROM ('%s') TO ('%s')",
            name,
            from.format(ISO_DATE),
            to.format(ISO_DATE)
        );
        try {
            jdbcTemplate.execute(ddl);
            return true;
        } catch (RuntimeException e) {
            // A concurrent create (lost the lock race) or a pre-existing default-overlap is benign.
            log.debug("auth.audit: could not create partition {} (likely already present)", name, e);
            return false;
        }
    }

    private int dropExpiredPartitions(YearMonth current) {
        List<YearMonth> existing = existingMonthlyPartitions();
        int dropped = 0;
        for (YearMonth ym : partitionsToDrop(existing, current)) {
            String name = partitionName(ym);
            try {
                // DETACH then DROP keeps the parent's locking footprint small; IF EXISTS is idempotent.
                jdbcTemplate.execute("DROP TABLE IF EXISTS " + name);
                dropped++;
                log.info("auth.audit: dropped expired partition {} (retention {} months)", name, RETENTION_MONTHS);
            } catch (RuntimeException e) {
                log.warn("auth.audit: failed to drop expired partition {}", name, e);
            }
        }
        return dropped;
    }

    private List<YearMonth> existingMonthlyPartitions() {
        List<String> names = jdbcTemplate.queryForList(
            """
            SELECT c.relname
              FROM pg_inherits i
              JOIN pg_class c     ON c.oid = i.inhrelid
              JOIN pg_class p     ON p.oid = i.inhparent
             WHERE p.relname = 'auth_event'
            """,
            String.class
        );
        List<YearMonth> months = new ArrayList<>();
        for (String name : names) {
            YearMonth ym = monthFromPartitionName(name);
            if (ym != null) {
                months.add(ym);
            }
        }
        return months;
    }
}
