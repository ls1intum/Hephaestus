package de.tum.cit.aet.hephaestus.practices.feedback;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Serializes the structured mentor brief stored in an {@code IN_CHAT} feedback body. */
public final class ConversationBriefBody {

    static final String KIND = "conversation-brief";

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private ConversationBriefBody() {}

    /**
     * The composer's notes to the mentor. Every field is <em>about</em> the turn; none of them is a line
     * <em>in</em> it.
     *
     * @param title names the issue, never the person - what the mentor is going to be raising
     * @param situation what the run saw, in the composer's terms and never addressed to the developer
     * @param capability the useful understanding or behaviour the conversation should support
     * @param evidenceSummary a concise account of the evidence grounding the note
     * @param inConversationSignal an observable sign that the conversation helped
     * @param alreadySaid where this has already been put to the developer, and what has moved without help
     */
    public record Brief(
            String title,
            String situation,
            String capability,
            String evidenceSummary,
            String inConversationSignal,
            @Nullable String alreadySaid) {}

    public static String render(
            String title,
            String situation,
            String capability,
            String evidenceSummary,
            String inConversationSignal,
            @Nullable String alreadySaid) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("kind", KIND);
        node.put("title", title);
        node.put("situation", situation);
        node.put("capability", capability);
        node.put("evidenceSummary", evidenceSummary);
        node.put("inConversationSignal", inConversationSignal);
        // Absent on every brief written before this field existed, and absent whenever nothing has been
        // said yet, which are different things the reader can tell apart from the rest of the note.
        if (alreadySaid != null && !alreadySaid.isBlank()) {
            node.put("alreadySaid", alreadySaid);
        }
        return node.toString();
    }

    public static @Nullable Brief parse(@Nullable String body) {
        JsonNode node = readBrief(body);
        if (node == null) {
            return null;
        }
        String title = text(node, "title");
        String situation = text(node, "situation");
        String capability = text(node, "capability");
        String evidenceSummary = text(node, "evidenceSummary");
        String inConversationSignal = text(node, "inConversationSignal");
        if (title == null
                || situation == null
                || capability == null
                || evidenceSummary == null
                || inConversationSignal == null) {
            return null;
        }
        return new Brief(
                title, situation, capability, evidenceSummary, inConversationSignal, text(node, "alreadySaid"));
    }

    public static boolean isBrief(@Nullable String body) {
        return parse(body) != null;
    }

    private static @Nullable JsonNode readBrief(@Nullable String body) {
        if (body == null || body.isBlank() || body.charAt(0) != '{') {
            return null;
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(body);
        } catch (JacksonException e) {
            return null;
        }
        return node.isObject() && KIND.equals(text(node, "kind")) ? node : null;
    }

    private static @Nullable String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString()) {
            return null;
        }
        String text = value.asString().strip();
        return text.isEmpty() ? null : text;
    }
}
