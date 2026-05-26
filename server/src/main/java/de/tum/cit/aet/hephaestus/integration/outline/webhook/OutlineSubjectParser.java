package de.tum.cit.aet.hephaestus.integration.outline.webhook;

import de.tum.cit.aet.hephaestus.integration.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.SubjectParser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Outline {@link SubjectParser}: {@code outline.<workspace>.<collection>.<event>} →
 * {@code EventTypeKey(OUTLINE, event)}.
 *
 * <p>Symmetric to {@link OutlineSubjectKeyDeriver}; the producer controls the subject,
 * so the parser strictly validates structure rather than defensively sanitizing input.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = true)
public class OutlineSubjectParser implements SubjectParser {

    private static final String PREFIX = "outline.";

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.OUTLINE;
    }

    @Override
    public EventTypeKey parse(String fullSubject) {
        if (fullSubject == null || !fullSubject.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Not an Outline subject: " + fullSubject);
        }
        String[] parts = fullSubject.split("\\.", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException(
                "Outline subject must be 4 dot-separated tokens (outline.workspace.collection.event), got: " +
                    fullSubject
            );
        }
        String eventType = parts[3];
        if (eventType.isBlank()) {
            throw new IllegalArgumentException("Outline subject has blank event token: " + fullSubject);
        }
        return new EventTypeKey(IntegrationKind.OUTLINE, eventType);
    }
}
