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
        // trigger a whole-workspace reconcile.
        return Set.of(Capability.WEBHOOK_INGEST);
    }

    /**
     * No delivery lanes: Outline's API would take a comment on a document, but no {@code SummaryChannel}
     * for it exists, and a claimed lane no channel fills is a delivery promise nothing keeps. Feedback
     * about a document lands on the Hephaestus surface instead.
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
