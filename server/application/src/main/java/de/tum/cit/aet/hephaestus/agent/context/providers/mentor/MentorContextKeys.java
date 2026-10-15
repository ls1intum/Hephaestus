package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.evidence.capture.MentorContextPaths;
import java.util.Set;

/**
 * Single source of truth for mentor context output keys. The Java {@code fetch_context}
 * whitelist + the runner's JS whitelist ({@code pi-mentor-protocol.ts#FETCH_CONTEXT_ALLOWED})
 * must agree; the Java side derives from the provider constants so a new provider auto-extends
 * the set, but the JS side needs a manual mirror — keep the full workspace-relative keys identical.
 */
public final class MentorContextKeys {

    public static final Set<String> ALLOWED_OUTPUT_KEYS = Set.of(
            UserContentSource.OUTPUT_KEY,
            WorkspaceContentSource.OUTPUT_KEY,
            PracticeCatalogContentSource.OUTPUT_KEY,
            ResultHistoryContentSource.OUTPUT_KEY,
            DeliveredFeedbackContentSource.OUTPUT_KEY,
            MentorContextPaths.RECENT_AUTHORED_WORK,
            MentorContextPaths.MERGE_READINESS,
            ReviewAttemptsContentSource.OUTPUT_KEY,
            "inputs/context/slack_conversations.json",
            PreparedConversationFeedbackContentSource.OUTPUT_KEY,
            CurrentThreadHistoryContentSource.OUTPUT_KEY,
            "inputs/context/outline_docs.json");

    /**
     * The runner's {@code FETCH_CONTEXT_MAX_CHARS}: beyond it the runner slices the JSON mid-document. Both count
     * UTF-16 units of compact JSON, and Jackson escapes no character more tersely than {@code JSON.stringify}, so a
     * document whose {@code writeValueAsString} fits reaches the model whole.
     */
    public static final int FETCH_CONTEXT_MAX_CHARS = MentorContextPaths.MAX_CHARS;

    private MentorContextKeys() {}
}
