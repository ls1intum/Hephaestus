package de.tum.in.www1.hephaestus.agent.runtime;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Pi practice-agent-specific runtime knobs. The image reference and pull policy live in
 * {@link AgentImageProperties} so practice and mentor stay aligned on a single agent-pi digest.
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.agent.pi")
public record PiAgentProperties(@DefaultValue("pi-runner.mjs") @NotBlank String runnerScript) {}
