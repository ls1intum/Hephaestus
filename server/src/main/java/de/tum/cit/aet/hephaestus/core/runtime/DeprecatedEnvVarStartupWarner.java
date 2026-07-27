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
 *   <li>LLM catalog: {@code HEPHAESTUS_WORKER_LLM_BASE_URL} / {@code
 *       HEPHAESTUS_WORKER_LLM_API_KEY} / {@code HEPHAESTUS_SANDBOX_LLM_PROXY_ENABLED} — LLM
 *       providers moved from env vars to the admin console.</li>
 *   <li>NATS→Postgres agent queue cutover: {@code AGENT_NATS_ENABLED} / {@code
 *       HEPHAESTUS_AGENT_NATS_SERVER} / {@code AGENT_NATS_MAX_ACK_PENDING} / {@code
 *       AGENT_NATS_FETCH_BATCH_SIZE} — the agent job queue is now the {@code agent_job} table,
 *       polled rather than pushed over NATS; every tuning knob the old JetStream consumer had
 *       (ack-pending, fetch batch size) has no poll-based equivalent and is simply dropped.</li>
 * </ul>
 *
 * <p>Deliberately checks the retired {@code hephaestus.agent.nats.*} keys, not any surviving property
 * with a similar name, and no shipped profile assigns one — a YAML-set placeholder default resolves
 * to a present-but-empty value rather than absent, which would make this warner fire on every boot.
 * A WARN here therefore means the operator's own deployment config still sets one of these.
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
        RETIRED_PROPERTIES.put(
            "hephaestus.agent.nats.max-ack-pending",
            "the agent queue now runs on PostgreSQL; there is no ack-pending equivalent for poll-based delivery"
        );
        RETIRED_PROPERTIES.put(
            "hephaestus.agent.nats.fetch-batch-size",
            "the agent queue now runs on PostgreSQL; set AGENT_CLAIM_BATCH_SIZE instead"
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
                log.warn("{} is set but no longer read — {}. Remove it from your deployment.", property, replacement);
            }
        });
    }
}
