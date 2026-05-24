package de.tum.cit.aet.hephaestus.integration.slack.manifest;

import de.tum.cit.aet.hephaestus.integration.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationManifest;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Slack manifest.
 *
 * <p>Per Salesforce 2025-05 Slack API ToS: message content is NEVER persisted —
 * {@code SlackMessageRef}/{@code SlackChannelRef} are value records only.
 * {@code BACKFILL_SYNC} is NOT declared (cannot persist messages → cannot backfill).
 *
 * <p>Scope reduced for #1198 first cut to capabilities that have wired SPI beans
 * (ingest + verification handshake + replay protection). {@code RATE_LIMITED},
 * {@code TOKEN_REFRESH}, {@code SCOPE_CHANGES}, {@code FEEDBACK_DELIVERY}, etc.
 * are re-added by the follow-up slice that lands those SPI beans (#1204 + the
 * credential converter follow-up). {@code IntegrationFrameworkBootstrap}
 * enforces the rule that every declared capability has its bean.
 *
 * <p>{@code matchIfMissing = true}: with the SPI beans now present, the manifest
 * is on by default so framework validation exercises the whole adapter on every
 * boot rather than only when the flag is explicitly set.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = true)
public class SlackManifest implements IntegrationManifest {

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.SLACK;
    }

    @Override
    public String displayName() {
        return "Slack";
    }

    @Override
    public Set<Capability> declaredCapabilities() {
        return Set.of(
            Capability.WEBHOOK_INGEST,
            Capability.URL_VERIFICATION_HANDSHAKE,
            Capability.REPLAY_PROTECTION
        );
    }
}
