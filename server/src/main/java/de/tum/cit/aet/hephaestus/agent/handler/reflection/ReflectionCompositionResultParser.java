package de.tum.cit.aet.hephaestus.agent.handler.reflection;

import de.tum.cit.aet.hephaestus.practices.feedback.StudentTextSanitizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Reads the composition stage's output off a finished job.
 *
 * <p>Never throws. The composition stage is additive: a review whose measurements landed is a successful
 * review whether or not anything was composed from them, so a malformed or absent payload yields an
 * empty list and the reflection surface simply gains nothing this cycle.
 *
 * <p>The payload the runner writes to {@code out/reflection-feedback.json} is surfaced by
 * {@code PiResultParser} under {@code output.reflectionFeedback}:
 *
 * <pre>{@code
 * { "messages": [ { "practiceSlug": "...", "title": "...", "body": "...", "nextStep": "..." } ] }
 * }</pre>
 */
@Component
public class ReflectionCompositionResultParser {

    private static final Logger log = LoggerFactory.getLogger(ReflectionCompositionResultParser.class);

    /** The key {@code PiResultParser} files the composition payload under on the job's output. */
    public static final String OUTPUT_KEY = "reflectionFeedback";

    /**
     * A bound on how much composed prose one cycle can offer, before the router's per-recipient cap
     * applies. Not a policy — a guard against a runaway turn filling the ledger.
     */
    static final int MAX_MESSAGES = 10;

    /**
     * The composed messages on this job's output, in the order the stage reported them.
     *
     * @param jobOutput the job's {@code output} column, or {@code null} for a job that produced none
     */
    public List<ComposedReflectionMessage> parse(@Nullable JsonNode jobOutput) {
        if (jobOutput == null || !jobOutput.isObject()) {
            return List.of();
        }
        JsonNode payload = jobOutput.get(OUTPUT_KEY);
        if (payload == null || !payload.isObject()) {
            return List.of();
        }
        JsonNode messages = payload.get("messages");
        if (messages == null || !messages.isArray()) {
            return List.of();
        }
        List<ComposedReflectionMessage> parsed = new ArrayList<>();
        // One message per practice: two messages about the same habit are the same message twice, and
        // the recipient would read the second as a separate problem.
        Set<String> seenPractices = new LinkedHashSet<>();
        for (JsonNode message : messages) {
            if (parsed.size() >= MAX_MESSAGES) {
                log.warn("Composition stage reported more than {} messages; the tail was ignored", MAX_MESSAGES);
                break;
            }
            ComposedReflectionMessage composed = read(message);
            if (composed == null) {
                continue;
            }
            if (!seenPractices.add(composed.practiceSlug())) {
                continue;
            }
            parsed.add(composed);
        }
        return List.copyOf(parsed);
    }

    private static @Nullable ComposedReflectionMessage read(JsonNode message) {
        if (message == null || !message.isObject()) {
            return null;
        }
        String practiceSlug = text(message, "practiceSlug", 128);
        String title = text(message, "title", ComposedReflectionMessage.MAX_TITLE_LENGTH);
        // Sanitised on the way in, not on the way out: this text is stored and then read verbatim by a
        // developer, and the same sanitiser already guards every other student-facing body.
        String body = StudentTextSanitizer.sanitize(text(message, "body", ComposedReflectionMessage.MAX_BODY_LENGTH));
        String nextStep = StudentTextSanitizer.sanitize(
            text(message, "nextStep", ComposedReflectionMessage.MAX_NEXT_STEP_LENGTH)
        );
        if (practiceSlug == null || title == null || body == null || nextStep == null) {
            return null;
        }
        ComposedReflectionMessage composed = new ComposedReflectionMessage(
            practiceSlug.toLowerCase(Locale.ROOT).replace('_', '-'),
            title,
            body,
            nextStep
        );
        return composed.isComplete() ? composed : null;
    }

    private static @Nullable String text(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString()) {
            return null;
        }
        String text = value.asString().strip();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
