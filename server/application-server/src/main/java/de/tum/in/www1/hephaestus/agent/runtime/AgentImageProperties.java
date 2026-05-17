package de.tum.in.www1.hephaestus.agent.runtime;

import de.tum.in.www1.hephaestus.agent.sandbox.ImagePullPolicy;
import jakarta.validation.constraints.NotBlank;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "hephaestus.agent.image")
public record AgentImageProperties(
    @DefaultValue("ghcr.io/ls1intum/hephaestus/agent-pi:latest") @NotBlank String reference,
    @DefaultValue("ALWAYS") ImagePullPolicy pullPolicy,
    @DefaultValue("false") boolean requireDigest
) {
    public static final String DIGEST_SUFFIX_DESCRIPTION = "@sha256:<64 lowercase hex>";
    public static final Pattern DIGEST_PATTERN = Pattern.compile(".+@sha256:[a-f0-9]{64}$");

    public boolean isDigestPinned() {
        return DIGEST_PATTERN.matcher(reference).matches();
    }
}
