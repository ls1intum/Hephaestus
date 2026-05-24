package de.tum.cit.aet.hephaestus.integration.github.manifest;

import de.tum.cit.aet.hephaestus.integration.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationManifest;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * GitHub manifest. Enabled by default ({@code matchIfMissing = true}) since the
 * #1198 first cut wires the universal SPI surface (webhook verifier, secret source,
 * subject deriver/parser, credential provider, token refresher) under
 * {@code integration/github/}. Capabilities that still depend on the legacy
 * {@code gitprovider.*} services (feedback delivery, inline findings, approval,
 * backfill sync, git content, status reporting, scope-change emission) are NOT
 * declared yet — they re-add as the C13 migration moves each legacy service into
 * the adapter package and registers the matching SPI bean.
 *
 * <p>Disable for tests / fixtures that intentionally exercise a degraded boot by
 * setting {@code hephaestus.integration.github.enabled=false}.
 */
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
            Capability.TOKEN_REFRESH
        );
    }
}
