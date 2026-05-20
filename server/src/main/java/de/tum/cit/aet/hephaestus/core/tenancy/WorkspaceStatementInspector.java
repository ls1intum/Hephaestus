package de.tum.cit.aet.hephaestus.core.tenancy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.tum.cit.aet.hephaestus.core.LoggingUtils;
import io.micrometer.core.instrument.Counter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hibernate {@link StatementInspector} that asserts every SQL statement against a
 * workspace-scoped table carries a {@code workspace_id} predicate.
 *
 * <p>Pipeline (cheapest checks first):
 * <ol>
 *   <li>Active bypass on the thread ({@code @WorkspaceAgnostic}) — pass</li>
 *   <li>Mode {@link TenancyEnforcement#OFF} — pass</li>
 *   <li>Caffeine cache hit on the literal SQL — return cached decision</li>
 *   <li>Word-boundary regex: {@code workspace_id} mentioned anywhere — pass</li>
 *   <li>Table-extract regex: pull table names from {@code FROM/JOIN/UPDATE/INTO} clauses,
 *       intersect with {@link WorkspaceScopedTables#scopedTables()}. Any match without
 *       a {@code workspace_id} reference anywhere in the statement is a violation.</li>
 * </ol>
 *
 * <p><b>Why regex, not a SQL parser?</b> JSqlParser was tried and rejected: adding it to
 * the classpath caused Spring Data JPA to auto-activate its {@code JSqlParserQueryEnhancer},
 * which fails on legitimate Postgres-escaped {@code @Query} natives (e.g.,
 * {@code CONCAT(:id\:\:text, ...)}), breaking application boot. A regex-only inspector
 * has no transitive blast radius and is sufficient for the {@code workspace_id} predicate
 * shape we actually care about.
 *
 * <p>Wired via Spring Boot's {@code HibernatePropertiesCustomizer} in
 * {@link TenancyConfiguration}.
 */
public class WorkspaceStatementInspector implements StatementInspector {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceStatementInspector.class);

    /**
     * Matches the {@code workspace_id} column as a word boundary. Tolerates every
     * predicate shape Hibernate emits (simple {@code col = ?}, tuple-IN
     * {@code (a, workspace_id) IN ((?, ?))}, schema-qualified
     * {@code wm1_0.workspace_id}, etc.). Trade-off: a literal string containing the
     * bare word would falsely pass. Acceptable because Hibernate-emitted SQL is
     * parameterized — column names rarely appear inside string-literal values — and
     * the alternative (tighter predicate matching) breaks legitimate composite-key
     * queries.
     */
    private static final Pattern WORKSPACE_ID_PATTERN = Pattern.compile("\\bworkspace_id\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Extracts table identifiers immediately following {@code FROM}, {@code JOIN},
     * {@code UPDATE}, {@code DELETE FROM}, or {@code INSERT INTO}. Captures the optional
     * schema-qualified name; {@link #unqualify(String)} strips the schema afterwards.
     * Designed to tolerate Hibernate-emitted SQL, Postgres-quoted identifiers, and
     * common edge cases without invoking a grammar-based parser.
     */
    private static final Pattern TABLE_REFERENCE_PATTERN = Pattern.compile(
        "(?:\\bFROM\\b|\\bJOIN\\b|\\bUPDATE\\b|\\bINTO\\b)\\s+(\"?[A-Za-z_][A-Za-z0-9_]*\"?(?:\\s*\\.\\s*\"?[A-Za-z_][A-Za-z0-9_]*\"?)?)",
        Pattern.CASE_INSENSITIVE
    );

    private final WorkspaceScopedTables scopedTables;
    private final TenancyEnforcement mode;
    private final TenancyViolationReporter reporter;
    private final Counter parseFailureCounter;

    private final Cache<String, Decision> decisionCache = Caffeine.newBuilder().maximumSize(10_000).build();

    public WorkspaceStatementInspector(
        WorkspaceScopedTables scopedTables,
        TenancyEnforcement mode,
        TenancyViolationReporter reporter,
        Counter parseFailureCounter
    ) {
        this.scopedTables = scopedTables;
        this.mode = mode;
        this.reporter = reporter;
        this.parseFailureCounter = parseFailureCounter;
    }

    @Override
    public String inspect(String sql) {
        if (sql == null || sql.isBlank()) return sql;
        if (mode == TenancyEnforcement.OFF) return sql;
        if (TenancyBypass.isActive()) return sql;

        Decision decision = decisionCache.get(sql, this::analyze);
        if (decision != null && decision.violated()) {
            reporter.report(sql, decision.unguardedTables(), mode);
        }
        return sql;
    }

    private Decision analyze(String sql) {
        if (WORKSPACE_ID_PATTERN.matcher(sql).find()) {
            return Decision.ok();
        }
        try {
            Set<String> unguarded = new HashSet<>();
            Matcher matcher = TABLE_REFERENCE_PATTERN.matcher(sql);
            while (matcher.find()) {
                String name = unqualify(matcher.group(1)).toLowerCase(Locale.ROOT);
                if (scopedTables.isScoped(name)) {
                    unguarded.add(name);
                }
            }
            return unguarded.isEmpty() ? Decision.ok() : Decision.violation(Set.copyOf(unguarded));
        } catch (Exception e) {
            // The inspector must NEVER throw. Fail-open with an observable counter so
            // pathological inputs surface in metrics rather than as request failures.
            parseFailureCounter.increment();
            if (log.isDebugEnabled()) {
                log.debug(
                    "Tenancy regex analysis failed ({}: {}): {}",
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    LoggingUtils.truncate(sql, 200)
                );
            }
            return Decision.ok();
        }
    }

    /** Strip dialect quotes and the schema prefix from an identifier. */
    private static String unqualify(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        // Drop schema prefix first (it may be quoted too).
        int dot = s.lastIndexOf('.');
        if (dot >= 0) {
            s = s.substring(dot + 1).trim();
        }
        if (s.length() >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            if ((first == '"' && last == '"') || (first == '`' && last == '`') || (first == '\'' && last == '\'')) {
                s = s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    record Decision(boolean violated, Set<String> unguardedTables) {
        static Decision ok() {
            return new Decision(false, Set.of());
        }

        static Decision violation(Set<String> tables) {
            return new Decision(true, tables);
        }
    }
}
