package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.SubjectParser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Slack {@link SubjectParser}: {@code slack.<team>.<channel>.<event>} →
 * {@code EventTypeKey(SLACK, event)}.
 *
 * <p>Subject is producer-controlled (see {@link SlackSubjectKeyDeriver}) so the parser
 * does NOT defensively decode untrusted segments — it only validates structure and
 * extracts the event slot. Malformed subjects throw {@link IllegalArgumentException}
 * so the JetStream consumer can surface the problem rather than silently routing
 * to a wrong handler.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackSubjectParser implements SubjectParser {

    private static final String PREFIX = "slack.";

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.SLACK;
    }

    @Override
    public EventTypeKey parse(String fullSubject) {
        if (fullSubject == null || !fullSubject.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Not a Slack subject: " + fullSubject);
        }
        String[] parts = fullSubject.split("\\.", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException(
                "Slack subject must be 4 dot-separated tokens (slack.team.channel.event), got: " + fullSubject
            );
        }
        String eventType = parts[3];
        if (eventType.isBlank()) {
            throw new IllegalArgumentException("Slack subject has blank event token: " + fullSubject);
        }
        return new EventTypeKey(IntegrationKind.SLACK, eventType);
    }
}
