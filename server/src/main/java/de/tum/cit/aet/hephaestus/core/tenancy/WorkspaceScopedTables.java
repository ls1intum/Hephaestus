package de.tum.cit.aet.hephaestus.core.tenancy;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import java.util.Set;
import java.util.TreeSet;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.persister.entity.EntityPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for workspace-scoped vs. global SQL tables.
 *
 * <p>At {@link ApplicationReadyEvent}, walks Hibernate's mapping metamodel for the
 * physical table name of every {@code @Entity} AND every many-to-many {@code @JoinTable}
 * collection. Any table not in {@link #GLOBAL_TABLES} is treated as workspace-scoped.
 *
 * <p>Join tables are critical: they have no {@code workspace_id} column of their own but
 * still need a predicate — they share the parent entity's tenancy boundary. A bare
 * {@code SELECT * FROM issue_label} without a join to the scoped parent leaks across
 * workspaces, so the inspector must treat join tables as scoped.
 *
 * <p>Using the physical metamodel (rather than reflecting on {@code @Table.name()})
 * avoids the silent landmine of entities without explicit {@code @Table.name} — Hibernate
 * emits snake_case while {@code entity.getName()} returns CamelCase.
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
        // Fleet-wide worker JWT revocation; worker JWTs are not workspace-scoped
        "worker_token_denylist",
        // Fleet-wide worker liveness/capacity registry (#1138); not workspace-scoped
        "worker_registry",
        // Liquibase machinery
        "databasechangelog",
        "databasechangeloglock"
    );

    private volatile Set<String> scopedTables = Set.of();
    private final ObjectProvider<EntityManagerFactory> entityManagerFactoryProvider;

    /**
     * Takes an {@link ObjectProvider} rather than {@link EntityManagerFactory} directly to
     * break a startup cycle: this bean is consumed (transitively) by Spring Boot's
     * {@code HibernatePropertiesCustomizer}, which itself is needed to BUILD the
     * {@code EntityManagerFactory}. Lazy resolution via {@code ObjectProvider} means the
     * EMF lookup only happens at {@link ApplicationReadyEvent}, after the EMF is fully
     * built.
     */
    public WorkspaceScopedTables(ObjectProvider<EntityManagerFactory> entityManagerFactoryProvider) {
        this.entityManagerFactoryProvider = entityManagerFactoryProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    void populateFromMetamodel() {
        EntityManagerFactory entityManagerFactory = entityManagerFactoryProvider.getObject();
        SessionFactoryImplementor sf = entityManagerFactory
            .unwrap(SessionFactory.class)
            .unwrap(SessionFactoryImplementor.class);
        MappingMetamodel metamodel = sf.getMappingMetamodel();
        Set<EntityType<?>> entities = entityManagerFactory.getMetamodel().getEntities();

        Set<String> tables = new TreeSet<>();
        for (EntityType<?> entity : entities) {
            Class<?> javaType = entity.getJavaType();
            if (javaType == null) continue;
            EntityPersister persister = metamodel.getEntityDescriptor(javaType);
            addIfScoped(tables, persister.getMappedTableDetails().getTableName());
        }
        // Many-to-many join tables + element-collection tables: physical tables with no
        // workspace_id column of their own. They inherit the parent's tenancy boundary,
        // and a bare SELECT against them leaks cross-workspace if not joined to a scoped
        // parent. Collections owned via a foreign-key column on the child entity resolve
        // to the child's own table — already added in the entity walk above.
        metamodel.forEachCollectionDescriptor(collection -> addIfScoped(tables, collection.getTableName()));

        // Fail-fast: zero scoped tables from a non-empty entity set means the Hibernate
        // mapping metamodel call chain regressed. A silently-empty set would turn the
        // inspector into a no-op — worst failure mode for a security control.
        if (tables.isEmpty() && !entities.isEmpty()) {
            throw new IllegalStateException(
                "WorkspaceScopedTables populated zero scoped tables from " +
                    entities.size() +
                    " entities. Tenancy enforcement would silently disable. " +
                    "Check Hibernate MappingMetamodel API compatibility."
            );
        }
        this.scopedTables = Set.copyOf(tables);
        log.info(
            "WorkspaceScopedTables populated: {} workspace-scoped tables (incl. join tables), {} global",
            scopedTables.size(),
            GLOBAL_TABLES.size()
        );
    }

    private static void addIfScoped(Set<String> tables, String rawName) {
        if (rawName == null || rawName.isBlank()) return;
        String name = rawName.toLowerCase();
        if (!GLOBAL_TABLES.contains(name)) {
            tables.add(name);
        }
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
