package de.tum.cit.aet.hephaestus.practices.feedback;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The one place that knows how a PREPARED conversation unit's stored brief is laid out, so the producer
 * and the reader cannot disagree about it.
 *
 * <p><b>Why anything is stored at all.</b> {@code ConversationalFeedbackPreparer} wrote a NULL body so
 * that no stale snippet was frozen at preparation time, and that reasoning still holds for a
 * <em>snippet</em>. What is stored here is not one: it is the composer's <em>move</em> — the question to
 * open with, the evidence to hold back until the developer has answered, and what the turn is trying to
 * leave them able to do. The mentor still writes every word of the turn with the live conversation in
 * front of it, so the contextual advantage a null body existed to protect is intact; what the mentor no
 * longer has to invent from scratch is the move itself.
 *
 * <p><b>Why it rides in {@code body} rather than in a column of its own.</b> Same trade
 * {@link InAppFeedbackBody} made for the in-app headline: {@code feedback} has one text column,
 * and a brief is composed text about one unit with the same lifetime, the same supersession and the same
 * TTL as the unit itself. A column would be a schema change for a payload that is already
 * unit-scoped. The cost is that {@code body} now has two shapes on two lanes, which is why
 * {@link #parse} exists and why it answers {@code null} for anything this class did not write — a caller
 * that guessed would read a coaching plan as words somebody was told.
 *
 * <p><b>JSON, not prose.</b> The three parts must reach the mentor separately and intact; a labelled
 * Markdown block would have to be split back apart on a delimiter the composer is free to type. Nothing
 * renders this to a person: an operator read is withheld on this lane exactly as it is on the in-app
 * lane, and the developer sees the mentor's words, never the brief.
 */
public final class ConversationBriefBody {

    /** Marks a body this class wrote. Anything else is prose some other producer stored. */
    static final String KIND = "conversation-brief";

    /** Bumped only if the shape below changes incompatibly; a body of another version reads back as absent. */
    static final int VERSION = 1;

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private ConversationBriefBody() {}

    /**
     * The move, as the composer wrote it.
     *
     * @param title names the issue, never the person - what the mentor is going to be raising
     * @param opener a question about how they work, asked before anything is told
     * @param evidence what to show once they have answered, and not before
     * @param target what the turn is trying to leave them able to do for themselves
     */
    public record Brief(String title, String opener, String evidence, String target) {}

    /** The stored body for one composed conversational move. */
    public static String render(String title, String opener, String evidence, String target) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("kind", KIND);
        node.put("version", VERSION);
        node.put("title", title);
        node.put("opener", opener);
        node.put("evidence", evidence);
        node.put("target", target);
        return node.toString();
    }

    /**
     * The move stored in {@code body}, or {@code null} for a body this class did not write.
     *
     * <p>Null is the honest answer rather than a best effort at reconstruction: a caller that invented a
     * brief out of prose would be putting a coaching plan in the composer's mouth, and a caller that read
     * a brief as prose would tell the developer what it was only ever meant to tell the mentor.
     */
    public static @Nullable Brief parse(@Nullable String body) {
        if (body == null || body.isBlank() || body.charAt(0) != '{') {
            return null;
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(body);
        } catch (JacksonException e) {
            return null;
        }
        if (!node.isObject() || !KIND.equals(text(node, "kind")) || node.path("version").asInt(-1) != VERSION) {
            return null;
        }
        String title = text(node, "title");
        String opener = text(node, "opener");
        String evidence = text(node, "evidence");
        String target = text(node, "target");
        if (title == null || opener == null || evidence == null || target == null) {
            return null;
        }
        return new Brief(title, opener, evidence, target);
    }

    /** Whether {@code body} is a coaching brief rather than words a person was shown. */
    public static boolean isBrief(@Nullable String body) {
        return parse(body) != null;
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
