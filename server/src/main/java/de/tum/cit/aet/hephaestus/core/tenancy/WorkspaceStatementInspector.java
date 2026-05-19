package de.tum.cit.aet.hephaestus.core.tenancy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContextHolder;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hibernate {@link StatementInspector} that asserts every SQL statement against a
 * workspace-scoped table carries a {@code workspace_id} predicate.
 *
 * <h2>Decision pipeline</h2>
 * <ol>
 *   <li><b>Bypass:</b> if {@code WorkspaceContextHolder.isBypassActive()} (a
 *       {@code @WorkspaceAgnostic}-advised method is on the stack), pass through.</li>
 *   <li><b>Mode OFF:</b> pass through (mode flag still resolved at construction).</li>
 *   <li><b>Cache hit:</b> Caffeine-cached decision keyed on the literal SQL.</li>
 *   <li><b>Regex fast path:</b> if the SQL contains {@code workspace_id} as a word, the
 *       predicate is present somewhere — pass through. Catches 99%+ of statements without
 *       parsing.</li>
 *   <li><b>JSqlParser slow path:</b> parse the SQL, walk tables, and if any referenced
 *       table is in {@code WorkspaceScopedTables} but the predicate is missing, fire a
 *       violation via the supplied {@link TenancyViolationReporter}.</li>
 * </ol>
 *
 * <p><b>Performance:</b> the cache is bounded (10k entries) and the cheap path returns
 * the original SQL unmodified. JSqlParser is invoked only when the regex says "no" AND
 * the table set intersects {@code WorkspaceScopedTables}.
 *
 * <p>Wired via {@code spring.jpa.properties.hibernate.session_factory.statement_inspector}.
 */
public class WorkspaceStatementInspector implements StatementInspector {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceStatementInspector.class);

    /** Word-boundary match for the column name; case-insensitive. */
    private static final Pattern WORKSPACE_ID_PATTERN =
        Pattern.compile("\\bworkspace_id\\b", Pattern.CASE_INSENSITIVE);

    private final WorkspaceScopedTables scopedTables;
    private final TenancyEnforcement mode;
    private final TenancyViolationReporter reporter;

    /** Bounded cache; key = raw SQL; value = previously-computed decision. */
    private final Cache<String, Decision> decisionCache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .build();

    public WorkspaceStatementInspector(
        WorkspaceScopedTables scopedTables,
        TenancyEnforcement mode,
        TenancyViolationReporter reporter
    ) {
        this.scopedTables = scopedTables;
        this.mode = mode;
        this.reporter = reporter;
    }

    @Override
    public String inspect(String sql) {
        if (sql == null || sql.isBlank()) return sql;
        if (mode == TenancyEnforcement.OFF) return sql;
        if (WorkspaceContextHolder.isBypassActive()) return sql;

        Decision decision = decisionCache.get(sql, this::analyze);
        if (decision != null && decision.violated()) {
            reporter.report(sql, decision.unguardedTables(), mode);
        }
        return sql;
    }

    /**
     * Decide whether the SQL is OK or violates tenancy. Pipeline: regex fast path →
     * JSqlParser slow path. Returns a Decision (cached).
     */
    private Decision analyze(String sql) {
        // Fast path: column name present anywhere → assume OK. This will miss the rare
        // case of "workspace_id" appearing in a string literal but not in WHERE; we accept
        // that false-negative because (a) it's extremely rare in practice and (b) catching
        // it would require full SQL analysis on every statement.
        if (WORKSPACE_ID_PATTERN.matcher(sql).find()) {
            return Decision.ok();
        }

        // Slow path: parse and intersect referenced tables with scoped table set.
        Set<String> unguarded;
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            TablesNamesFinder finder = new TablesNamesFinder<>();
            Set<String> tableNames = new HashSet<>(finder.getTables(statement));
            unguarded = new HashSet<>();
            for (String raw : tableNames) {
                String name = unquote(raw).toLowerCase();
                if (scopedTables.isScoped(name)) {
                    unguarded.add(name);
                }
            }
        } catch (JSQLParserException e) {
            // Native SQL with dynamic identifiers or DB-specific syntax that JSqlParser
            // can't grammar-match: log once at DEBUG and treat as not-a-violation to
            // avoid false positives on Hibernate's batch / DDL / metadata SQL.
            if (log.isDebugEnabled()) {
                log.debug("JSqlParser could not parse SQL ({}): {}", e.getMessage(), summarize(sql));
            }
            return Decision.ok();
        }

        if (unguarded.isEmpty()) {
            return Decision.ok();
        }
        return Decision.violation(Set.copyOf(unguarded));
    }

    /** Strip surrounding quotes that JSqlParser preserves on dialect-quoted identifiers. */
    private static String unquote(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.length() >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            if ((first == '"' && last == '"') || (first == '`' && last == '`') || (first == '\'' && last == '\'')) {
                s = s.substring(1, s.length() - 1);
            }
        }
        // Drop schema prefix: schema.table → table
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }

    private static String summarize(String sql) {
        if (sql == null) return "<null>";
        return sql.length() > 200 ? sql.substring(0, 200) + "..." : sql;
    }

    /** Cached outcome of analyzing a SQL string. */
    record Decision(boolean violated, Set<String> unguardedTables) {
        static Decision ok() {
            return new Decision(false, Set.of());
        }

        static Decision violation(Set<String> tables) {
            return new Decision(true, tables);
        }
    }
}
