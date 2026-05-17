package de.tum.in.www1.hephaestus.agent.sandbox;

import de.tum.in.www1.hephaestus.agent.runtime.AgentImageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "hephaestus.agent.image", name = "require-digest", havingValue = "true")
public class AgentImagePinGuard {

    public AgentImagePinGuard(AgentImageProperties properties) {
        if (!properties.isDigestPinned()) {
            throw new IllegalStateException(
                "hephaestus.agent.image.reference must be digest-pinned (ending in " +
                    AgentImageProperties.DIGEST_SUFFIX_DESCRIPTION +
                    ") when hephaestus.agent.image.require-digest=true. Got: " +
                    properties.reference() +
                    ". See docs/admin/agent-image-digests.md."
            );
        }
    }
}
