package de.tum.cit.aet.hephaestus.core.tenancy;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import java.util.Set;
import java.util.TreeSet;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for workspace-scoped vs. global SQL tables.
 *
 * <p>At {@link ApplicationReadyEvent}, walks Hibernate's mapping metamodel for the
 * physical table name of every {@code @Entity}; any table not in {@link #GLOBAL_TABLES}
 * is treated as workspace-scoped. Using the physical metamodel (rather than reflecting on
 * {@code @Table.name()}) avoids the silent landmine of entities without an explicit
 * {@code @Table.name} — Hibernate's naming strategy emits snake_case while
 * {@code entity.getName()} returns CamelCase, which would mis-match parser-extracted
 * table names from {@link WorkspaceStatementInspector}.
 *
 * <p>Adding to {@link #GLOBAL_TABLES} is a security decision: each entry carries a
 * one-line rationale and must be reviewed.
 */
@Component
public class WorkspaceScopedTables {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceScopedTables.class);

    public static final Set<String> GLOBAL_TABLES = Set.of(
        // Tenant root + identity
        "workspace",
        "workspace_slug_history",
        "user",
        "user_preferences",
        "user_achievement",
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

    public WorkspaceScopedTables(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @EventListener(ApplicationReadyEvent.class)
    void populateFromMetamodel() {
        SessionFactoryImplementor sf = entityManagerFactory
            .unwrap(SessionFactory.class)
            .unwrap(SessionFactoryImplementor.class);
        Set<EntityType<?>> entities = entityManagerFactory.getMetamodel().getEntities();
        Set<String> tables = new TreeSet<>();
        for (EntityType<?> entity : entities) {
            Class<?> javaType = entity.getJavaType();
            if (javaType == null) continue;
            EntityPersister persister = sf.getMappingMetamodel().getEntityDescriptor(javaType);
            String tableName = persister.getMappedTableDetails().getTableName().toLowerCase();
            if (!GLOBAL_TABLES.contains(tableName)) {
                tables.add(tableName);
            }
        }
        // Fail-fast: an empty result with entities present means the Hibernate API call
        // chain (currently @Incubating MappingMetamodel) silently regressed. Silently
        // empty scopedTables turns the WorkspaceStatementInspector into a no-op, which
        // is the worst possible failure mode for a security control.
        if (tables.isEmpty() && !entities.isEmpty()) {
            throw new IllegalStateException(
                "WorkspaceScopedTables populated zero scoped tables from " + entities.size()
                    + " entities. Tenancy enforcement would silently disable. "
                    + "Check Hibernate MappingMetamodel API compatibility."
            );
        }
        this.scopedTables = Set.copyOf(tables);
        log.info(
            "WorkspaceScopedTables populated: {} workspace-scoped, {} global",
            scopedTables.size(),
            GLOBAL_TABLES.size()
        );
    }

    /** Workspace-scoped physical table names (lowercase). Empty until ApplicationReady fires. */
    public Set<String> scopedTables() {
        return scopedTables;
    }

    /** True iff the table requires a {@code workspace_id} predicate on every query. */
    public boolean isScoped(String tableName) {
        return tableName != null && scopedTables.contains(tableName.toLowerCase());
    }
}
