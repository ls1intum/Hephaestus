package de.tum.cit.aet.hephaestus.integration.scm.github.webhook;

import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.SubjectParser;
import org.springframework.stereotype.Component;

/**
 * GitHub adapter for {@link SubjectParser}. Re-encodes the three GitHub event tiers
 * (repository / organization / installation) into the {@link EventTypeKey} prefix so
 * downstream handler routing stays vendor-agnostic:
 * <ul>
 *   <li>{@code github.<org>.<repo>.<event>} → {@code repository.<event>}
 *   <li>{@code github.<org>.?.<event>} → {@code organization.<event>}
 *   <li>{@code github.?.?.<event>} → {@code installation.<event>}
 * </ul>
 *
 * <p>The literal {@code ?} placeholder is reserved (matches the producer-side
 * {@link GithubSubjectKeyDeriver}). Subjects with fewer than four dot-delimited
 * components are malformed and throw {@link IllegalArgumentException} — the consumer
 * pipeline turns those into a dead-letter rather than a silent drop.
 */
@Component
public class GithubSubjectParser implements SubjectParser {

    private static final String PREFIX = "github.";
    private static final String PLACEHOLDER = "?";

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public EventTypeKey parse(String fullSubject) {
        if (fullSubject == null || fullSubject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (!fullSubject.startsWith(PREFIX)) {
            throw new IllegalArgumentException("subject must start with 'github.': " + fullSubject);
        }
        String[] parts = fullSubject.split("\\.", -1);
        if (parts.length < 4) {
            throw new IllegalArgumentException(
                "subject must have >= 4 dot-separated components, got " + parts.length + ": " + fullSubject
            );
        }
        // parts[0] = "github", parts[1] = org, parts[2] = repo, parts[3..] = event (may carry suffixes).
        String org = parts[1];
        String repo = parts[2];
        // Rejoin tail in case the event name itself contained dots (sanitized to ~ by the deriver,
        // but defensive — split on the deriver's contract preserves all tail segments).
        StringBuilder eventBuilder = new StringBuilder(parts[3]);
        for (int i = 4; i < parts.length; i++) {
            eventBuilder.append('.').append(parts[i]);
        }
        String event = eventBuilder.toString();
        if (event.isBlank()) {
            throw new IllegalArgumentException("event segment must not be blank: " + fullSubject);
        }

        String tier;
        if (PLACEHOLDER.equals(org) && PLACEHOLDER.equals(repo)) {
            tier = "installation";
        } else if (PLACEHOLDER.equals(repo)) {
            tier = "organization";
        } else {
            tier = "repository";
        }
        return new EventTypeKey(IntegrationKind.GITHUB, tier + "." + event);
    }
}
