package de.tum.cit.aet.hephaestus.agent.mentor;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

/**
 * When llmProvider + credentialMode + modelName are all set, mentor uses them directly and
 * skips the workspace-scoped AgentConfig table. Omit any one to fall back to AgentConfig.
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.mentor.agent")
public record MentorAgentProperties(
    @DefaultValue("100000") @Min(1) int maxPromptChars,
    @DefaultValue("") String baseUrl,
    @Nullable LlmProvider llmProvider,
    @Nullable CredentialMode credentialMode,
    @Nullable String llmApiKey,
    @Nullable String modelName,
    @DefaultValue("600") @Min(30) int timeoutSeconds
) {}
