package de.tum.cit.aet.hephaestus.integration.slack.manifest;

import de.tum.cit.aet.hephaestus.integration.core.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Per-kind capability declaration for Slack. */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
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
        // Slack declares no framework Capability — deliberately, and this is the honest state.
        //
        // Inbound Events API messages arrive on Slack's OWN signature-verified controller
        // (SlackEventsController + SlackSignatureVerifier) and outbound goes through SlackMessageService.
        // Neither rides the generic capability lanes the bootstrap enforces:
        //   * WEBHOOK_INGEST requires per-kind WebhookSignatureVerifier / WebhookSecretSource /
        //     SubjectKeyDeriver / SubjectParser beans (the NATS-published webhook lane). Slack has none
        //     — its events do not fan out onto the JetStream subject grammar (ConsumerSubjectMath is
        //     empty for SLACK); they are handled in-process. Declaring WEBHOOK_INGEST here would make
        //     IntegrationFrameworkBootstrap fail fast for four missing beans.
        //   * Conversational feedback is delivered as a ContentSource PULL on the mentor turn, not an
        //     OutboundChannel push, so it is not a manifest capability either.
        //
        // Promoting Slack to a first-class WEBHOOK_INGEST/outbound citizen (extracting the
        // EventProducer/OutboundChannel/WebhookEndpoint ports) is a deliberate future evolution of the integration
        // framework, not a gap in this class: the in-process handling above is correct and complete for how Slack
        // works today. The port extraction is specified in the integration-framework converged design
        // (.ai/notes/integration-framework-design.md §6) and its planned ADR. Until it lands, an empty set is the
        // truthful declaration.
        return Set.of();
    }
}
