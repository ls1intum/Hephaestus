package de.tum.in.www1.hephaestus.agent.sandbox;

import de.tum.in.www1.hephaestus.agent.runtime.AgentImageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fails application startup when {@code hephaestus.agent.image.reference} is not pinned by
 * digest (i.e. does not end in {@code @sha256:<64 hex>}). The guard runs at bean construction —
 * before any sandbox bootstrapper attempts to pull — so the failure mode is "container does not
 * start", not "review jobs silently consume a tag-mutated image".
 *
 * <p>Enabled per environment via {@code hephaestus.agent.image.require-digest=true}; production
 * sets this in {@code application-prod.yml}. Dev keeps the default ({@code false}) so {@code
 * :latest} continues to work for local iteration.
 */
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
