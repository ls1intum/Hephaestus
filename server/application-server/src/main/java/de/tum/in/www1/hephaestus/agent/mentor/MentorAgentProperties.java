package de.tum.in.www1.hephaestus.agent.mentor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Docker image + entrypoint for the mentor (interactive) Pi container. Bound from
 * {@code hephaestus.mentor.agent.*} so it does not collide with
 * {@code de.tum.in.www1.hephaestus.agent.sandbox.InteractiveSandboxProperties}
 * which binds {@code hephaestus.mentor}.
 *
 * @param image           Docker image reference (defaults to {@code :latest}; in prod pin to {@code @sha256:...}
 *                        for reproducible builds — matches the {@code hephaestus.agent.pi} sibling)
 * @param runnerScript    classpath resource under {@code agent/} run by the mentor container
 * @param maxPromptChars  hard cap on a single user prompt's character length before the
 *                        controller rejects with a synthetic error chunk. Default 100k chars
 *                        ≈ 25k tokens — well above any pedagogically meaningful question,
 *                        well below provider context windows. Configurable so an operator
 *                        running an unusually high-context model can raise it without
 *                        recompiling.
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.mentor.agent")
public record MentorAgentProperties(
    @DefaultValue("ghcr.io/ls1intum/hephaestus/agent-pi-mentor:latest") @NotBlank String image,
    @DefaultValue("pi-mentor-runner.mjs") @NotBlank String runnerScript,
    @DefaultValue("100000") @Min(1) int maxPromptChars
) {}
