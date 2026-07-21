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
 * Warns once at boot when a deployment still sets an env var that the LLM catalog (#1368) retired
 * and now silently ignores — see {@code MIGRATION.md}, "LLM provider configuration moved from env
 * vars to the admin console". Mirrors {@link RuntimeRoleStartupLogger}'s
 * {@code ApplicationReadyEvent} + {@link Environment} pattern: nothing binds these properties to a
 * {@code @ConfigurationProperties} record anymore, so this reads the raw values directly rather
 * than relying on a bean that no longer exists to fail loudly.
 */
@Component
public class DeprecatedLlmEnvVarStartupWarner {

    private static final Logger log = LoggerFactory.getLogger(DeprecatedLlmEnvVarStartupWarner.class);

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
    }

    private final Environment environment;

    public DeprecatedLlmEnvVarStartupWarner(Environment environment) {
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
