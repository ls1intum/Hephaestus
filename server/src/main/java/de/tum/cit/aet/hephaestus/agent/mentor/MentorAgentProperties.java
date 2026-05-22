package de.tum.cit.aet.hephaestus.agent.mentor;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Instance-level mentor knobs. LLM provider / credentials / model / timeout all come from the
 * workspace-scoped {@link de.tum.cit.aet.hephaestus.agent.config.AgentConfig}; the only knobs
 * worth setting per deployment are the prompt-size guard and an optional base-URL override for
 * routing OpenAI/Anthropic traffic through a proxy via the hephaestus provider extension.
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.mentor.agent")
public record MentorAgentProperties(@DefaultValue("100000") @Min(1) int maxPromptChars, @DefaultValue("") String baseUrl) {}
