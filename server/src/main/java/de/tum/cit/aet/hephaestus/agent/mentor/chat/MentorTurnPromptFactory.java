package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import de.tum.cit.aet.hephaestus.agent.runtime.PiRuntimeFactory;
import de.tum.cit.aet.hephaestus.mentor.ThreadSurface;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Owns runner-prompt assembly for one mentor turn — the single place that decorates the developer's
 * message with a per-surface style directive before it reaches the runner.
 *
 * <p><strong>Design.</strong> The directive text lives in a classpath resource
 * ({@code agent/mentor/prompts/slack-dm-turn.md}), loaded once via {@link
 * PiRuntimeFactory#loadClasspathResource} — the same mechanism that already externalises the mentor
 * SYSTEM prompt ({@code agent/mentor/system.md}, loaded by {@code MentorPiAdapter}). Multi-paragraph
 * prompt prose does not belong as a Java text block inline in a service class: it can't be reviewed,
 * diffed, or edited like prose, and it bloats the class with content that has nothing to do with turn
 * orchestration. Externalising it here mirrors the system-prompt precedent instead of inventing a new
 * pattern, and needs no template-engine dependency — the resource carries two plain
 * {@code {{PLACEHOLDER}}} markers, split out once at class-init into fixed prefix/middle/suffix
 * segments so a turn only ever concatenates strings (no repeated scanning of already-substituted,
 * developer-controlled text, which a naive {@code String#replace} chain could re-match).
 *
 * <p><strong>Contract.</strong> This only builds the transient string sent to the Pi runner for the
 * CURRENT turn. It never touches persistence: {@link MentorTurnRequest#userMessage()} is stored
 * verbatim by {@code MentorTurnPersistence} regardless of surface — only the runner-bound prompt is
 * decorated. The system prompt itself stays static per (shared) sandbox, so per-surface adaptation has
 * to ride the turn instead.
 */
final class MentorTurnPromptFactory {

    private static final String SLACK_DM_TEMPLATE_RESOURCE = "mentor/prompts/slack-dm-turn.md";
    private static final String USER_MESSAGE_PLACEHOLDER = "{{USER_MESSAGE}}";
    private static final String THREAD_HISTORY_PLACEHOLDER = "{{THREAD_HISTORY}}";
    private static final String CURRENT_THREAD_HISTORY_KEY = "inputs/context/current_thread_history.json";
    private static final int MAX_HISTORY_CHARS = 12_000;

    private static final String SLACK_DM_PREFIX;
    private static final String SLACK_DM_MIDDLE;
    private static final String SLACK_DM_SUFFIX;

    static {
        String template = new String(
            PiRuntimeFactory.loadClasspathResource(SLACK_DM_TEMPLATE_RESOURCE),
            StandardCharsets.UTF_8
        );
        int userIdx = template.indexOf(USER_MESSAGE_PLACEHOLDER);
        int historyIdx = template.indexOf(THREAD_HISTORY_PLACEHOLDER);
        if (userIdx < 0 || historyIdx < 0 || historyIdx < userIdx) {
            throw new IllegalStateException(
                "Malformed " +
                    SLACK_DM_TEMPLATE_RESOURCE +
                    ": expected " +
                    USER_MESSAGE_PLACEHOLDER +
                    " followed by " +
                    THREAD_HISTORY_PLACEHOLDER
            );
        }
        SLACK_DM_PREFIX = template.substring(0, userIdx);
        SLACK_DM_MIDDLE = template.substring(userIdx + USER_MESSAGE_PLACEHOLDER.length(), historyIdx);
        SLACK_DM_SUFFIX = template.substring(historyIdx + THREAD_HISTORY_PLACEHOLDER.length());
    }

    private MentorTurnPromptFactory() {}

    /**
     * The prompt actually sent to the runner for this turn. WEB gets the developer's message
     * unchanged; {@link ThreadSurface#SLACK_DM} gets it wrapped in the Slack style directive plus the
     * visible thread history (see class javadoc — the persisted message is untouched either way).
     */
    static String forRunner(MentorTurnRequest request, Map<String, byte[]> contextInputs) {
        if (request.surface() != ThreadSurface.SLACK_DM) {
            return request.userMessage();
        }
        return (
            SLACK_DM_PREFIX +
            request.userMessage() +
            SLACK_DM_MIDDLE +
            visibleThreadHistory(contextInputs) +
            SLACK_DM_SUFFIX
        );
    }

    private static String visibleThreadHistory(Map<String, byte[]> contextInputs) {
        byte[] bytes = contextInputs.get(CURRENT_THREAD_HISTORY_KEY);
        if (bytes == null || bytes.length == 0) {
            return "{}";
        }
        String text = new String(bytes, StandardCharsets.UTF_8).strip();
        if (text.isEmpty()) {
            return "{}";
        }
        return text.length() <= MAX_HISTORY_CHARS ? text : text.substring(text.length() - MAX_HISTORY_CHARS);
    }
}
