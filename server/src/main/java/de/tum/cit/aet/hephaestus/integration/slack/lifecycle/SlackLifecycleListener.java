package de.tum.cit.aet.hephaestus.integration.slack.lifecycle;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationLifecycleListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Minimal lifecycle listener required by the integration framework bootstrap for every
 * registered kind. The Slack persistence layer (slack_channel / slack_message) has been
 * removed — all methods fall through to the no-op defaults on
 * {@link IntegrationLifecycleListener}.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackLifecycleListener implements IntegrationLifecycleListener {

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.SLACK;
    }
}
