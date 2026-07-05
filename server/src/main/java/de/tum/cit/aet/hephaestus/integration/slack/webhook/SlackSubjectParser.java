package de.tum.cit.aet.hephaestus.integration.slack.webhook;

import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.SubjectParser;
import org.springframework.stereotype.Component;

/**
 * Slack adapter for {@link SubjectParser}. Slack has a single flat event tier (no
 * repository/organization/installation split like GitHub), so the parse simply lifts the
 * trailing event token out of {@code slack.<team>.<channel>.<event>}:
 * <ul>
 *   <li>{@code slack.<team>.<channel>.message} → {@code EventTypeKey(SLACK, "message")}
 * </ul>
 *
 * <p>Subjects with fewer than four dot-delimited components are malformed and throw
 * {@link IllegalArgumentException}; the consumer dispatcher turns that into a debug-logged
 * no-op ACK rather than a silent drop (mirrors {@code GithubSubjectParser}).
 */
@Component
public class SlackSubjectParser implements SubjectParser {

    private static final String PREFIX = "slack.";

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.SLACK;
    }

    @Override
    public EventTypeKey parse(String fullSubject) {
        if (fullSubject == null || fullSubject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (!fullSubject.startsWith(PREFIX)) {
            throw new IllegalArgumentException("subject must start with 'slack.': " + fullSubject);
        }
        String[] parts = fullSubject.split("\\.", -1);
        if (parts.length < 4) {
            throw new IllegalArgumentException(
                "subject must have >= 4 dot-separated components, got " + parts.length + ": " + fullSubject
            );
        }
        // parts[0] = "slack", parts[1] = team, parts[2] = channel, parts[3..] = event.
        StringBuilder eventBuilder = new StringBuilder(parts[3]);
        for (int i = 4; i < parts.length; i++) {
            eventBuilder.append('.').append(parts[i]);
        }
        String event = eventBuilder.toString();
        if (event.isBlank()) {
            throw new IllegalArgumentException("event segment must not be blank: " + fullSubject);
        }
        return new EventTypeKey(IntegrationKind.SLACK, event);
    }
}
