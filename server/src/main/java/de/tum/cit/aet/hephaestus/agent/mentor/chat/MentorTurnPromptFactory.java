package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import de.tum.cit.aet.hephaestus.agent.runtime.PiRuntimeFactory;
import de.tum.cit.aet.hephaestus.mentor.ThreadSurface;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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
