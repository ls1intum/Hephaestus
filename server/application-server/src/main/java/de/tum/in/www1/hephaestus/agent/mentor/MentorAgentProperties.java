package de.tum.in.www1.hephaestus.agent.mentor;

import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.sandbox.ImagePullPolicy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
    @DefaultValue("ghcr.io/ls1intum/hephaestus/agent-pi:latest") @NotBlank String image,
    @DefaultValue("100000") @Min(1) int maxPromptChars,
    @DefaultValue("") String baseUrl,
    @Nullable LlmProvider llmProvider,
    @Nullable CredentialMode credentialMode,
    @Nullable String llmApiKey,
    @Nullable String modelName,
    @DefaultValue("600") @Min(30) int timeoutSeconds,
    @DefaultValue("IF_NOT_PRESENT") ImagePullPolicy pullPolicy
) {}
