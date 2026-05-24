package de.tum.cit.aet.hephaestus.integration.knowledge.spi;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;

/**
 * Per-document access check for vendors with granular per-page permissions (Notion).
 *
 * <p>Without this SPI, posting a comment on a non-shared page produces a 404 cascade
 * the agent has to interpret. With it, access is checked typed before delivery and
 * the agent surfaces the right "missing permission" message.
 */
public interface DocumentAccessProbe {

    IntegrationKind kind();

    AccessResult probe(IntegrationRef ref, String documentId);

    sealed interface AccessResult permits AccessResult.Granted, AccessResult.Denied, AccessResult.Unknown {
        record Granted() implements AccessResult {}
        record Denied(String reason) implements AccessResult {}
        record Unknown(String reason) implements AccessResult {}
    }
}
