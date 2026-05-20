package de.tum.cit.aet.hephaestus.core.tenancy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.tum.cit.aet.hephaestus.core.LoggingUtils;
import io.micrometer.core.instrument.Counter;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.TablesNamesFinder;
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
 *   <li>Regex {@code \bworkspace_id\b} present — pass<br>
 *       (Known false-negative: SQL containing the literal string {@code workspace_id} in a
 *       value would pass this fast path without parsing. Acceptable because Hibernate-emitted
 *       SQL is parameterized and rarely contains column names as literals.)</li>
 *   <li>JSqlParser slow path: parse, walk tables, flag any scoped table without the predicate.
 *       Parse failures (Hibernate dialect quirks, DDL, {@code {h-schema}} tokens) increment
 *       {@code tenancy.parse_failure.total} and pass through — fail-open is documented and
 *       observable rather than silent.</li>
 * </ol>
 *
 * <p>Wired via Spring Boot's {@code HibernatePropertiesCustomizer} in
 * {@link TenancyConfiguration}.
 */
public class WorkspaceStatementInspector implements StatementInspector {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceStatementInspector.class);

    /**
     * Matches {@code workspace_id} only when it appears in a predicate-shaped position
     * (followed by a comparison operator or {@code IN}/{@code IS}). String literals
     * containing the bare word — {@code "refers to workspace_id mapping"} — must NOT
     * short-circuit; they fall through to the JSqlParser slow path.
     */
    private static final Pattern WORKSPACE_ID_PREDICATE_PATTERN = Pattern.compile(
        "\\bworkspace_id\\s*(=|!=|<>|>=?|<=?|IN\\b|IS\\b)",
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
        if (WORKSPACE_ID_PREDICATE_PATTERN.matcher(sql).find()) {
            return Decision.ok();
        }
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            TablesNamesFinder<?> finder = new TablesNamesFinder<>();
            Set<String> tableNames = new HashSet<>(finder.getTables(statement));
            Set<String> unguarded = new HashSet<>();
            for (String raw : tableNames) {
                String name = unqualify(raw).toLowerCase();
                if (scopedTables.isScoped(name)) {
                    unguarded.add(name);
                }
            }
            return unguarded.isEmpty() ? Decision.ok() : Decision.violation(Set.copyOf(unguarded));
        } catch (Exception e) {
            // JSqlParser throws JSQLParserException for grammar failures, TokenMgrException
            // for lexer failures (e.g., backslash-escaped Postgres casts like \:\:text in
            // @Query native queries), and occasionally NullPointerException on edge-case
            // inputs. The inspector must NEVER throw — fail-open with an observable counter.
            parseFailureCounter.increment();
            if (log.isDebugEnabled()) {
                log.debug(
                    "JSqlParser could not parse SQL ({}: {}): {}",
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    LoggingUtils.truncate(sql, 200)
                );
            }
            return Decision.ok();
        }
    }

    /** Strip dialect quotes and schema/catalog prefixes from an identifier. */
    private static String unqualify(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.length() >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            if ((first == '"' && last == '"') || (first == '`' && last == '`') || (first == '\'' && last == '\'')) {
                s = s.substring(1, s.length() - 1);
            }
        }
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
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
