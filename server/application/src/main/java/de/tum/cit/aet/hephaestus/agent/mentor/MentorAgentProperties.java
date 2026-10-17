package de.tum.cit.aet.hephaestus.agent.mentor;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBinding;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Instance-level mentor knobs. LLM provider / credentials / model / timeout all come from the
 * workspace's {@code MENTOR} {@link WorkspaceAgentBinding} (via
 * {@link LlmModelResolver}); the only knob worth setting
 * per deployment is the prompt-size guard.
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.mentor.agent")
public record MentorAgentProperties(
        @DefaultValue("100000") @Min(1) int maxPromptChars) {}
