package de.tum.in.www1.hephaestus.agent.runtime;

import de.tum.in.www1.hephaestus.agent.sandbox.ImagePullPolicy;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The runner script name is owned by {@code PracticeRunnerProfile} — operator overrides for it
 * were never used in practice and risked drifting from the V8 flags / per-process env that the
 * kernel pairs with the script. Bumping the runner is a code change.
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.agent.pi")
public record PiAgentProperties(
    @DefaultValue("ghcr.io/ls1intum/hephaestus/agent-pi:latest") @NotBlank String image,
    @DefaultValue("IF_NOT_PRESENT") ImagePullPolicy pullPolicy
) {}
