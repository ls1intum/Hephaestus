package de.tum.cit.aet.hephaestus.integration.spi;

import java.util.List;

/**
 * Capability-gated SPI for posting inline findings (SCM diff notes, knowledge-base
 * document-anchor comments). Kinds that don't declare {@link Capability#INLINE_FINDINGS}
 * never resolve via this registry — Slack and similar messaging vendors are
 * compile-time excluded.
 */
public interface InlineFindingChannel {
    IntegrationKind kind();

    InlineResult postInlineFindings(FeedbackChannel.FeedbackTarget target, List<InlineFinding> findings);

    record InlineFinding(FindingAnchor anchor, String body, String marker) {}

    record InlineResult(int posted, int failed) {}
}
