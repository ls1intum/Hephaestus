package de.tum.in.www1.hephaestus.agent.mentor;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Docker image + entrypoint for the mentor (interactive) Pi container. Bound from
 * {@code hephaestus.mentor.agent.*} so it does not collide with
 * {@code de.tum.in.www1.hephaestus.agent.sandbox.InteractiveSandboxProperties}
 * which binds {@code hephaestus.mentor}.
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.mentor.agent")
public record MentorAgentProperties(
    @DefaultValue("ghcr.io/ls1intum/hephaestus/agent-pi-mentor:latest") @NotBlank String image,
    @DefaultValue("pi-mentor-runner.mjs") @NotBlank String runnerScript
) {}
