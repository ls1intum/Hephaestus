package de.tum.cit.aet.hephaestus.integration.outline.manifest;

import de.tum.cit.aet.hephaestus.integration.core.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Per-kind capability declaration for Outline. */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
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
        // Outline change notifications ride the unified /webhooks/{kind} JetStream lane (ADR 0023 §3):
        // a signature-verified delivery is published to the durable `outline` stream and consumed to
        // trigger a whole-workspace reconcile. WEBHOOK_INGEST binds the four SPI beans the bootstrap
        // validates — OutlineWebhookSignatureVerifier, OutlineWebhookSecretSource, OutlineSubjectKeyDeriver,
        // OutlineSubjectParser. Outline still emits no observations/findings; it remains a content source.
        return Set.of(Capability.WEBHOOK_INGEST);
    }
}
