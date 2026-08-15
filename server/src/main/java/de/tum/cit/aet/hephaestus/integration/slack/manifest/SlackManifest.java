package de.tum.cit.aet.hephaestus.integration.slack.manifest;

import de.tum.cit.aet.hephaestus.integration.core.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationManifest;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Per-kind capability declaration for Slack. */
@Component
public class SlackManifest implements IntegrationManifest {

    private final boolean slackEnabled;

    public SlackManifest(@Value("${hephaestus.integration.slack.enabled:false}") boolean slackEnabled) {
        this.slackEnabled = slackEnabled;
    }

    @Override
    public boolean enabled() {
        return slackEnabled;
    }

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
        return Set.of(Capability.WEBHOOK_INGEST);
    }

    /**
     * Conversations do get reviewed, but the descriptor lives with the agent module that projects the
     * thread, and the review is triggered by a scheduler reading quiescence, not by any ingested Slack
     * event — there is no vendor provenance here for Slack to claim.
     */
    @Override
    public ReviewContribution reviewContribution() {
        return ReviewContribution.none();
    }
}
