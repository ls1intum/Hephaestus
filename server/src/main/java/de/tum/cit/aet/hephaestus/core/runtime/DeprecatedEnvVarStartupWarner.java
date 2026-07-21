package de.tum.cit.aet.hephaestus.core.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Warns once at boot when a deployment still sets an env var that Hephaestus has retired and now
 * silently ignores — see {@code MIGRATION.md}. Reads the raw {@link Environment} values directly
 * rather than relying on a {@code @ConfigurationProperties} bean that no longer exists to fail
 * loudly, so a retired property never gets a second life by accident binding onto an unrelated
 * record.
 *
 * <p>Covers two retirements:
 * <ul>
 *   <li>#1368 (LLM catalog): {@code HEPHAESTUS_WORKER_LLM_BASE_URL} / {@code
 *       HEPHAESTUS_WORKER_LLM_API_KEY} / {@code HEPHAESTUS_SANDBOX_LLM_PROXY_ENABLED} — LLM
 *       providers moved from env vars to the admin console.</li>
 *   <li>#1368 (NATS→Postgres agent queue cutover): {@code AGENT_NATS_ENABLED} / {@code
 *       hephaestus.agent.nats.server} — the agent job queue is now the {@code agent_job} table,
 *       polled rather than pushed over NATS.</li>
 * </ul>
 */
@Component
public class DeprecatedEnvVarStartupWarner {

    private static final Logger log = LoggerFactory.getLogger(DeprecatedEnvVarStartupWarner.class);

    /** Retired property (dotted form — {@link Environment} resolves the env-var form too) -> replacement guidance. */
    private static final Map<String, String> RETIRED_PROPERTIES = new LinkedHashMap<>();

    static {
        RETIRED_PROPERTIES.put(
            "hephaestus.worker.llm.base-url",
            "providers are configured in the admin console under Instance admin → AI models"
        );
        RETIRED_PROPERTIES.put(
            "hephaestus.worker.llm.api-key",
            "providers are configured in the admin console under Instance admin → AI models"
        );
        RETIRED_PROPERTIES.put(
            "hephaestus.sandbox.llm-proxy.enabled",
            "the LLM proxy now runs automatically wherever agent jobs execute; there is no standalone enable flag"
        );
        RETIRED_PROPERTIES.put(
            "hephaestus.agent.nats.enabled",
            "the agent queue now runs on PostgreSQL; set AGENT_ENABLED instead"
        );
        RETIRED_PROPERTIES.put(
            "hephaestus.agent.nats.server",
            "the agent queue now runs on PostgreSQL; set AGENT_ENABLED instead"
        );
    }

    private final Environment environment;

    public DeprecatedEnvVarStartupWarner(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warnOnRetiredProperties() {
        RETIRED_PROPERTIES.forEach((property, replacement) -> {
            if (environment.getProperty(property) != null) {
                log.warn(
                    "{} is set but no longer read (#1368) — {}. Remove it from your deployment.",
                    property,
                    replacement
                );
            }
        });
    }
}
