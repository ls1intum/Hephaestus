package de.tum.cit.aet.hephaestus.agent.mentor;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Instance-level mentor knobs. LLM provider / credentials / model / timeout all come from the
 * workspace-scoped {@link de.tum.cit.aet.hephaestus.agent.config.AgentConfig} (via
 * {@link de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver}); the only knobs worth setting
 * per deployment are the prompt-size guard and an optional base-URL override.
 *
 * <p>{@code baseUrl} is a residual, mentor-specific config source flagged in #1368 slice 5's design
 * (not folded into the shared catalog): it applies ONLY when the resolved mentor config is a
 * pre-catalog (legacy) binding with no explicit {@code llmBaseUrl} of its own — see
 * {@code MentorPiAdapter#buildSandboxSpec}. A catalog-bound (instance or workspace BYO) mentor config
 * always uses its connection's own base URL and ignores this property.
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.mentor.agent")
public record MentorAgentProperties(
    @DefaultValue("100000") @Min(1) int maxPromptChars,
    @DefaultValue("") String baseUrl
) {}
