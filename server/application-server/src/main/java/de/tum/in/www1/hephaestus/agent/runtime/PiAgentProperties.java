package de.tum.in.www1.hephaestus.agent.runtime;

import de.tum.in.www1.hephaestus.agent.sandbox.docker.ImagePullPolicy;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "hephaestus.agent.pi")
public record PiAgentProperties(
    @DefaultValue("ghcr.io/ls1intum/hephaestus/agent-pi:latest") @NotBlank String image,
    @DefaultValue("pi-runner.mjs") @NotBlank String runnerScript,
    @DefaultValue("IF_NOT_PRESENT") ImagePullPolicy pullPolicy
) {}
