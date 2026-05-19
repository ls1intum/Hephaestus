package de.tum.cit.aet.hephaestus.core.tenancy;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.hibernate.cfg.AvailableSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires tenancy enforcement: registers the {@link WorkspaceStatementInspector} as a
 * Hibernate {@code session_factory.statement_inspector} via Spring Boot's
 * {@link HibernatePropertiesCustomizer}, exposes the {@link TenancyViolationReporter}
 * bean, and creates the Micrometer counter.
 */
@Configuration
@EnableConfigurationProperties(TenancyEnforcementProperties.class)
public class TenancyConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TenancyConfiguration.class);

    @Bean
    Counter tenancyViolationCounter(MeterRegistry registry) {
        return Counter.builder("tenancy.violation.total")
            .description("Count of SQL statements against workspace-scoped tables that lacked a workspace_id predicate.")
            .tag("module", "tenancy")
            .register(registry);
    }

    @Bean
    TenancyViolationReporter tenancyViolationReporter(
        Counter tenancyViolationCounter,
        MeterRegistry registry
    ) {
        return (sql, unguardedTables, mode) -> {
            // Per-table counter increment for granular dashboards
            for (String table : unguardedTables) {
                registry.counter(
                    "tenancy.violation.total",
                    "module", "tenancy",
                    "table", table,
                    "mode", mode.name().toLowerCase()
                ).increment();
            }
            tenancyViolationCounter.increment();

            log.warn(
                "Tenancy violation ({}): scoped tables {} queried without workspace_id predicate. SQL: {}",
                mode.name().toLowerCase(),
                unguardedTables,
                summarize(sql)
            );
            if (mode == TenancyEnforcement.THROW) {
                throw new TenancyViolationException(unguardedTables);
            }
        };
    }

    @Bean
    WorkspaceStatementInspector workspaceStatementInspector(
        WorkspaceScopedTables scopedTables,
        TenancyEnforcementProperties properties,
        TenancyViolationReporter reporter
    ) {
        log.info("Workspace tenancy enforcement mode: {}", properties.enforcement());
        return new WorkspaceStatementInspector(scopedTables, properties.enforcement(), reporter);
    }

    /**
     * Wire the inspector into Hibernate via Spring Boot's HibernatePropertiesCustomizer
     * (the supported API for late binding of session-factory settings; using
     * {@code spring.jpa.properties.hibernate.session_factory.statement_inspector} alone
     * doesn't accept a bean reference).
     */
    @Bean
    HibernatePropertiesCustomizer tenancyInspectorHibernateCustomizer(
        WorkspaceStatementInspector inspector
    ) {
        return properties -> properties.put(AvailableSettings.STATEMENT_INSPECTOR, inspector);
    }

    private static String summarize(String sql) {
        if (sql == null) return "<null>";
        return sql.length() > 200 ? sql.substring(0, 200) + "..." : sql;
    }
}
