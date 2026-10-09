package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.evidence.capture.ContentSource;
import de.tum.cit.aet.hephaestus.evidence.capture.MentorContextPaths;
import java.util.Set;

/**
 * The fixed context documents that a Heph turn's context folder stages; {@code ContextFolderIndex} describes each in
 * the folder's {@code INDEX.md}, and a test keeps the two equal. An item path ({@code merge_readiness/<id>}, {@code issue/<id>}, {@code results_history/<id>},
 * {@code review_attempts/<kind>/<id>}) is not listed here: its owning source answers it through
 * {@link ContentSource#read}.
 */
public final class MentorContextKeys {

    public static final Set<String> ALLOWED_OUTPUT_KEYS = Set.of(
            UserContentSource.OUTPUT_KEY,
            WorkspaceContentSource.OUTPUT_KEY,
            PracticeCatalogContentSource.OUTPUT_KEY,
            ResultHistoryContentSource.OUTPUT_KEY,
            DeliveredFeedbackContentSource.OUTPUT_KEY,
            MentorContextPaths.RECENT_AUTHORED_WORK,
            MentorContextPaths.ACTIVITY,
            PracticeProfileContentSource.OUTPUT_KEY,
            MentorContextPaths.MERGE_READINESS,
            ReviewAttemptsContentSource.OUTPUT_KEY,
            ContentSource.OUTPUT_PREFIX + "slack_conversations.json",
            PreparedConversationFeedbackContentSource.OUTPUT_KEY,
            WeeklyDigestContentSource.OUTPUT_KEY,
            ContentSource.OUTPUT_PREFIX + "outline_docs.json");

    private MentorContextKeys() {}
}
