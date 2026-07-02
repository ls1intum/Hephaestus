package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import java.util.Set;

/**
 * Single source of truth for mentor context output keys. The Java {@code fetch_context}
 * whitelist + the runner's JS whitelist ({@code pi-mentor-runner.mjs#FETCH_CONTEXT_ALLOWED})
 * must agree; the Java side derives from the provider constants so a new provider auto-extends
 * the set, but the JS side needs a manual mirror — keep the file basenames identical.
 */
public final class MentorContextKeys {

    public static final Set<String> ALLOWED_OUTPUT_KEYS = Set.of(
        UserContentSource.OUTPUT_KEY,
        WorkspaceContentSource.OUTPUT_KEY,
        PracticeCatalogContentSource.OUTPUT_KEY,
        ObservationHistoryContentSource.OUTPUT_KEY,
        PracticeStandingContentSource.OUTPUT_KEY,
        DeliveredFeedbackContentSource.OUTPUT_KEY,
        RecentAuthoredWorkContentSource.OUTPUT_KEY,
        SlackConversationContentSource.OUTPUT_KEY
    );

    private MentorContextKeys() {}
}
