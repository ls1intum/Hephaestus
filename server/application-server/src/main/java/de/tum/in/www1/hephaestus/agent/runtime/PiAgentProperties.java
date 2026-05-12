package de.tum.in.www1.hephaestus.agent.runtime;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the Pi runtime.
 *
 * <p>Example configuration in {@code application.yml}:
 * <pre>{@code
 * hephaestus:
 *   agent:
 *     pi:
 *       image: ghcr.io/ls1intum/hephaestus/agent-pi:latest
 *       runner-script: pi-runner.mjs
 *       pull-on-startup: false
 * }</pre>
 *
 * @param image          Docker image reference (defaults to {@code :latest}; in prod pin to {@code @sha256:...})
 * @param runnerScript   classpath resource under {@code agent/} run by the Pi container
 * @param pullOnStartup  if {@code true}, pull the image into the local Docker cache on app boot
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.agent.pi")
public record PiAgentProperties(
    @DefaultValue("ghcr.io/ls1intum/hephaestus/agent-pi:latest") @NotBlank String image,
    @DefaultValue("pi-runner.mjs") @NotBlank String runnerScript,
    @DefaultValue("false") boolean pullOnStartup
) {}
