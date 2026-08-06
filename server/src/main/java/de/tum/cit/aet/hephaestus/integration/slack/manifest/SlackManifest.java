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
        return Set.of(Capability.WEBHOOK_INGEST);
    }

    /**
     * Nothing, and stated rather than assumed.
     *
     * <p>Slack does review conversations, but the artifact it reviews is a thread <em>projected</em> in
     * the agent module out of ingested messages, not an entity this integration owns — so no module
     * contributes a descriptor for it and Slack has no kind to observe. It is also raised by a
     * scheduler rather than by any ingested event, which the contract has no way to express and should
     * not pretend to: an empty {@code raises} is the truth, and a chat descriptor is the fix, not a
     * declaration written ahead of one.
     */
    @Override
    public ReviewContribution reviewContribution() {
        return ReviewContribution.none();
    }
}
