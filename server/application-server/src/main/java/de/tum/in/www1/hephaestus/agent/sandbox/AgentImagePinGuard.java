package de.tum.in.www1.hephaestus.agent.sandbox;

import de.tum.in.www1.hephaestus.agent.runtime.AgentImageProperties;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "hephaestus.agent.image", name = "require-digest", havingValue = "true")
public class AgentImagePinGuard {

    private static final Pattern DIGEST = Pattern.compile(".+@sha256:[a-f0-9]{64}$");

    public AgentImagePinGuard(AgentImageProperties properties) {
        if (!DIGEST.matcher(properties.reference()).matches()) {
            throw new IllegalStateException(
                "hephaestus.agent.image.reference must be digest-pinned (ending in @sha256:<64 lowercase hex>) " +
                    "when hephaestus.agent.image.require-digest=true. Got: " +
                    properties.reference() +
                    ". See docs/admin/agent-image-digests.md."
            );
        }
    }
}
