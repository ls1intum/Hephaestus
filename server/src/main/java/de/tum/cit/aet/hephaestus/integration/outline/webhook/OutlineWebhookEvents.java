package de.tum.cit.aet.hephaestus.integration.outline.webhook;

import java.util.List;

/**
 * The Outline document lifecycle events Hephaestus subscribes to upstream. Single source of truth for
 * the subscription registrar; every event changes mirrored content and so warrants a whole-workspace
 * reconcile on the consumer side.
 */
public final class OutlineWebhookEvents {

    /** Dotted event names as Outline emits and subscribes them. */
    public static final List<String> DOCUMENT_EVENTS = List.of(
        "documents.create",
        "documents.publish",
        "documents.unpublish",
        "documents.update",
        "documents.archive",
        "documents.unarchive",
        "documents.delete",
        "documents.move"
    );

    private OutlineWebhookEvents() {}
}
