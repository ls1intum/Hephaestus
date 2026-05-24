package de.tum.cit.aet.hephaestus.integration.outline.manifest;

import de.tum.cit.aet.hephaestus.integration.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationManifest;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Outline manifest.
 *
 * <p>Per-webhook-subscription signing secrets (Notion-style). Capture flow lands with
 * #1203 alongside the subscription store; for #1198 first cut the manifest only
 * declares ingest so the framework can validate the wired SPI beans. The richer
 * capability set ({@code TOKEN_REFRESH}, {@code BACKFILL_SYNC},
 * {@code FEEDBACK_DELIVERY}, {@code RATE_LIMITED}) re-appears as their respective
 * SPI beans land.
 *
 * <p>{@code matchIfMissing = true}: ingest beans are wired now, so the manifest is on
 * by default — bootstrap exercises the framework even when the property isn't set.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = true)
public class OutlineManifest implements IntegrationManifest {

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.OUTLINE;
    }

    @Override
    public String displayName() {
        return "Outline";
    }

    @Override
    public Set<Capability> declaredCapabilities() {
        return Set.of(Capability.WEBHOOK_INGEST);
    }
}
