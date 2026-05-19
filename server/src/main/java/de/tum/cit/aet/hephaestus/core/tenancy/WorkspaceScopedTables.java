package de.tum.cit.aet.hephaestus.core.tenancy;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for workspace tenancy table classification.
 *
 * <p>Populated at startup from the JPA metamodel: every {@code @Entity} whose
 * {@code @Table.name()} is NOT in {@link #GLOBAL_TABLES} is treated as workspace-scoped.
 * Consumed by:
 * <ul>
 *   <li>{@link WorkspaceStatementInspector} — to decide which SQL statements need a
 *       {@code workspace_id} predicate</li>
 *   <li>{@code DataIsolationArchitectureTest} — to cross-check its hand-curated entity sets
 *       against runtime reflection</li>
 * </ul>
 *
 * <p><b>Why a single source of truth?</b> Two separate lists drift over time. Every new
 * workspace-scoped entity becomes automatically protected when it lands; only legitimately
 * global tables need a one-line entry in {@link #GLOBAL_TABLES} with a rationale.
 *
 * <p><b>Allowlist discipline:</b> {@link #GLOBAL_TABLES} entries get a {@code // reason}
 * comment. Adding a table here is a security decision — reviewers must understand why.
 */
@Component
public class WorkspaceScopedTables {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceScopedTables.class);

    /**
     * Tables that legitimately have NO {@code workspace_id} predicate. Every entry has a
     * specific rationale — when in doubt, do NOT add to this list.
     */
    public static final Set<String> GLOBAL_TABLES = Set.of(
        // Tenant root + identity
        "workspace",                  // tenant root entity itself
        "workspace_slug_history",     // slug redirects; identifies workspaces by slug
        "user",                       // users span workspaces
        "user_preferences",           // user-scoped, cross-workspace
        "user_achievement",           // per-user progress across workspaces
        // Synced upstream identity (workspace linked separately via FK)
        "organization",
        "git_provider",
        "issue_type",
        // Vendor pricing (#1071: model pricing is global, not tenant-scoped)
        "model_pricing",
        // Liquibase machinery
        "databasechangelog",
        "databasechangeloglock"
    );

    private volatile Set<String> scopedTables = Set.of();
    private final EntityManagerFactory entityManagerFactory;

    @Autowired
    public WorkspaceScopedTables(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @EventListener(ApplicationReadyEvent.class)
    void populateFromMetamodel() {
        Set<String> tables = new TreeSet<>();
        for (EntityType<?> entity : entityManagerFactory.getMetamodel().getEntities()) {
            Class<?> javaType = entity.getJavaType();
            if (javaType == null) continue;
            Table table = javaType.getAnnotation(Table.class);
            String tableName = (table != null && !table.name().isBlank())
                ? table.name().toLowerCase()
                : entity.getName().toLowerCase();
            if (!GLOBAL_TABLES.contains(tableName)) {
                tables.add(tableName);
            }
        }
        this.scopedTables = Set.copyOf(tables);
        log.info(
            "WorkspaceScopedTables populated: {} workspace-scoped, {} global (allowlist)",
            scopedTables.size(),
            GLOBAL_TABLES.size()
        );
    }

    /** Workspace-scoped table names (lowercase, unquoted). Empty until ApplicationReady fires. */
    public Set<String> scopedTables() {
        return scopedTables;
    }

    /** Returns true if the table requires a {@code workspace_id} predicate on every query. */
    public boolean isScoped(String tableName) {
        return tableName != null && scopedTables.contains(tableName.toLowerCase());
    }
}
