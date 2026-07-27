package de.tum.cit.aet.hephaestus.core.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Warns once at boot when a deployment still sets an env var that Hephaestus has retired and now
 * silently ignores — see {@code MIGRATION.md}.
 *
 * <p><b>These are environment-variable names, verbatim, not dotted property names.</b> That is the
 * whole point of the class and it is easy to get wrong: {@code hephaestus.agent.nats.enabled} looks
 * like the right key, but Spring resolves it only from {@code HEPHAESTUS_AGENT_NATS_ENABLED} — and the
 * var operators actually set was {@code AGENT_NATS_ENABLED}, with no prefix. A dotted entry for an
 * unprefixed var can never fire. {@code DeprecatedEnvVarStartupWarnerTest} pins the shape.
 *
 * <p>A WARN must mean the operator's own config sets one of these, so:
 *
 * <ul>
 *   <li>no shipped {@code application-*.yml} may still read one through a {@code ${VAR:…}}
 *       placeholder — that would be live config, not a leftover;
 *   <li>a blank value counts as absent. {@code docker/compose.app.yaml} forwards each retired var as
 *       {@code ${VAR:-}} for one release so a stale line in the operator's {@code docker/.env} reaches
 *       the JVM at all (Compose {@code .env} entries are interpolation inputs, not container
 *       environment — without the forward this warner is blind on every Compose deployment). Unset,
 *       that forward delivers an empty string, which must not warn on every boot.
 * </ul>
 *
 * <p>Deliberately absent: {@code NATS_SERVER}. It fed the retired {@code hephaestus.agent.nats.server},
 * but it is still the live setting for webhook and sync ingest, so warning on it would fire on every
 * correctly configured deployment.
 */
@Component
public class DeprecatedEnvVarStartupWarner {

    private static final Logger log = LoggerFactory.getLogger(DeprecatedEnvVarStartupWarner.class);

    /** Retired environment variable (exact name) -&gt; replacement guidance. */
    private static final Map<String, String> RETIRED_ENV_VARS = new LinkedHashMap<>();

    static {
        RETIRED_ENV_VARS.put(
            "HEPHAESTUS_WORKER_LLM_BASE_URL",
            "providers are configured in the admin console under Instance admin → AI models"
        );
        RETIRED_ENV_VARS.put(
            "HEPHAESTUS_WORKER_LLM_API_KEY",
            "providers are configured in the admin console under Instance admin → AI models"
        );
        RETIRED_ENV_VARS.put(
            "HEPHAESTUS_SANDBOX_LLM_PROXY_ENABLED",
            "the LLM proxy now runs automatically wherever agent jobs execute; there is no standalone enable flag"
        );
        RETIRED_ENV_VARS.put("AGENT_NATS_ENABLED", "the agent queue now runs on PostgreSQL; set AGENT_ENABLED instead");
        RETIRED_ENV_VARS.put(
            "AGENT_NATS_MAX_ACK_PENDING",
            "the agent queue now runs on PostgreSQL; there is no ack-pending equivalent for poll-based delivery"
        );
        RETIRED_ENV_VARS.put(
            "AGENT_NATS_FETCH_BATCH_SIZE",
            "the agent queue now runs on PostgreSQL; set AGENT_CLAIM_BATCH_SIZE instead"
        );
    }

    /** The vars this warner fires on, so the regression guards can assert the shipped config agrees. */
    static Set<String> retiredEnvVarNames() {
        return Collections.unmodifiableSet(RETIRED_ENV_VARS.keySet());
    }

    private final Environment environment;

    public DeprecatedEnvVarStartupWarner(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warnOnRetiredProperties() {
        RETIRED_ENV_VARS.forEach((envVar, replacement) -> {
            String value = environment.getProperty(envVar);
            if (value != null && !value.isBlank()) {
                log.warn("{} is set but no longer read — {}. Remove it from your deployment.", envVar, replacement);
            }
        });
    }
}
