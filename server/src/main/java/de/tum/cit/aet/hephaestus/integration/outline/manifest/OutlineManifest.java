package de.tum.cit.aet.hephaestus.integration.outline.manifest;

import de.tum.cit.aet.hephaestus.integration.core.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import de.tum.cit.aet.hephaestus.integration.outline.domain.signal.DocsSignals;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Per-kind capability declaration for Outline. */
@Component
public class OutlineManifest implements IntegrationManifest {

    private final boolean outlineEnabled;

    public OutlineManifest(@Value("${hephaestus.integration.outline.enabled:false}") boolean outlineEnabled) {
        this.outlineEnabled = outlineEnabled;
    }

    @Override
    public boolean enabled() {
        return outlineEnabled;
    }

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
        // OutlineSubjectParser.
        return Set.of(Capability.WEBHOOK_INGEST);
    }

    /**
     * Documents, and the three events about them that carry review meaning.
     *
     * <p>This used to be {@code none()}, with a comment explaining that the emptiness was the defect the
     * contract had been built to make visible: eleven ingested lifecycle events, a complete webhook
     * stack, and nothing any of them could start. Closing it took a descriptor in this module and these
     * two lines — and no edit anywhere in the practices module, which was the point of the exercise.
     *
     * <p><b>Raises is a subset, and deliberately.</b> The descriptor lists what can happen to a document
     * in general; Outline raises the three it actually delivers. It observes {@code docs.document}
     * because it is the module that writes the mirror those documents live in.
     *
     * <p><b>Delivers nothing.</b> Outline's API would take a comment on a document, but no
     * {@code SummaryChannel} for it exists, so there is no lane to claim. A review of a document
     * therefore records its observations and shows them on the Hephaestus surface — which is what the
     * descriptor's {@code PROFILE}-only lane says — instead of a delivery promise nothing keeps.
     */
    @Override
    public ReviewContribution reviewContribution() {
        return new ReviewContribution(
            Set.of(DocsSignals.DOCUMENT),
            Map.of(
                DocsSignals.DOCUMENT,
                Set.of(DocsSignals.DOCUMENT_PUBLISHED, DocsSignals.DOCUMENT_UPDATED, DocsSignals.DOCUMENT_ARCHIVED)
            ),
            Map.of()
        );
    }
}
