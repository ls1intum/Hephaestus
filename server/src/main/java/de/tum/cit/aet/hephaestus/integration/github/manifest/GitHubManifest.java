package de.tum.cit.aet.hephaestus.integration.github.manifest;

import de.tum.cit.aet.hephaestus.integration.core.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Per-kind capability declaration for GitHub. Disable with
 *  {@code hephaestus.integration.github.enabled=false}. */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.github.enabled", havingValue = "true", matchIfMissing = true)
public class GitHubManifest implements IntegrationManifest {

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public String displayName() {
        return "GitHub";
    }

    @Override
    public Set<Capability> declaredCapabilities() {
        return Set.of(
            Capability.WEBHOOK_INGEST,
            Capability.TOKEN_REFRESH,
            Capability.FEEDBACK_DELIVERY,
            Capability.INLINE_FINDINGS,
            Capability.APPROVAL_WORKFLOW
        );
    }
}
