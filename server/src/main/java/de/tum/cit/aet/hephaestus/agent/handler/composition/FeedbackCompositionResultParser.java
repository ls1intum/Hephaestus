package de.tum.cit.aet.hephaestus.agent.handler.composition;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.StudentTextSanitizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * review whether or not anything was composed from them, so a malformed or absent payload yields an empty
 * list and the surfaces simply gain nothing this cycle.
 *
 * <p><b>Everything the runner checked is checked again here, and that is not belt-and-braces.</b> The
 * runner executes inside the sandbox alongside the model; its validation exists so the model is corrected
 * while it can still fix the unit. This class is the boundary a payload has to cross to become something
 * a person reads, and it trusts nothing it did not verify.
 *
 * <p>The payload the runner writes to {@code out/feedback.json} is surfaced by {@code PiResultParser}
 * under {@code output.feedback}. It is self-describing on purpose — it carries the observations the
 * composer was shown and the thread keys it was offered, so a unit can be checked against what was
 * actually on the table rather than against a re-query that may have moved since:
 *
 * <pre>{@code
 * {
 *   "observations": [ { "id": "obs-0", "practiceSlug": "...", "anchorable": true,
 *                       "citations": [ { "index": 0, "path": "...", "side": "NEW",
 *                                        "startLine": 47, "anchorable": true } ] } ],
 *   "preparedThreadKeys": [ "..." ],
 *   "units": [ { "channel": "IN_CONTEXT", "practiceSlug": "...", "basedOn": [ "obs-0" ],
 *                "action": "NEW", "title": "...", "body": "...", "nextStep": "...",
 *                "anchor": { "observationId": "obs-0", "citationIndex": 0 } } ]
 * }
 * }</pre>
 */
@Component
public class FeedbackCompositionResultParser {

    private static final Logger log = LoggerFactory.getLogger(FeedbackCompositionResultParser.class);

    /** The key {@code PiResultParser} files the composition payload under on the job's output. */
    public static final String OUTPUT_KEY = "feedback";

    /**
     * A bound on how much composed prose one cycle can offer, before each lane's per-recipient cap
     * applies. Not a policy — a guard against a runaway turn filling the ledger.
     */
    static final int MAX_UNITS = 24;

    private static final int MAX_PRACTICE_SLUG_LENGTH = 128;

    /**
     * The composed units on this job's output, in the order the stage reported them.
     *
     * @param jobOutput the job's {@code output} column, or {@code null} for a job that produced none
     */
    public List<ComposedFeedbackUnit> parse(@Nullable JsonNode jobOutput) {
        if (jobOutput == null || !jobOutput.isObject()) {
            return List.of();
        }
        JsonNode payload = jobOutput.get(OUTPUT_KEY);
        if (payload == null || !payload.isObject()) {
            return List.of();
        }
        JsonNode units = payload.get("units");
        if (units == null || !units.isArray()) {
            return List.of();
        }
        Map<String, StagedObservation> observations = readObservations(payload.get("observations"));
        Set<String> preparedThreadKeys = readThreadKeys(payload.get("preparedThreadKeys"));

        List<ComposedFeedbackUnit> parsed = new ArrayList<>();
        // One unit per practice per channel: two messages about the same habit on the same surface are
        // the same message twice, and the recipient would read the second as a separate problem.
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode unit : units) {
            if (parsed.size() >= MAX_UNITS) {
                log.warn("Composition stage reported more than {} units; the tail was ignored", MAX_UNITS);
                break;
            }
            ComposedFeedbackUnit composed = read(unit, observations, preparedThreadKeys);
            if (composed == null) {
                continue;
            }
            if (!seen.add(composed.channel().name() + ':' + composed.practiceSlug())) {
                continue;
            }
            parsed.add(composed);
        }
        return List.copyOf(parsed);
    }

    /** The units for one lane, in the order the stage reported them. */
    public List<ComposedFeedbackUnit> parse(@Nullable JsonNode jobOutput, FeedbackChannel channel) {
        return parse(jobOutput)
            .stream()
            .filter(unit -> unit.channel() == channel)
            .toList();
    }

    private static @Nullable ComposedFeedbackUnit read(
        JsonNode unit,
        Map<String, StagedObservation> observations,
        Set<String> preparedThreadKeys
    ) {
        if (unit == null || !unit.isObject()) {
            return null;
        }
        FeedbackChannel channel = channelOf(unit);
        ComposedFeedbackUnit.Action action = actionOf(unit);
        String practiceSlug = text(unit, "practiceSlug", MAX_PRACTICE_SLUG_LENGTH);
        if (channel == null || action == null || practiceSlug == null) {
            return null;
        }
        List<String> basedOn = strings(unit.get("basedOn"));
        if (basedOn.isEmpty()) {
            // A unit that names nothing it rests on cannot be bound to any evidence, and an unbound
            // message is an assertion the ledger could never justify afterwards.
            return null;
        }

        if (action == ComposedFeedbackUnit.Action.WITHHOLD) {
            ComposedFeedbackUnit.WithholdReason reason = withholdReasonOf(unit);
            return reason == null
                ? null
                : new ComposedFeedbackUnit(
                      channel,
                      normalizeSlug(practiceSlug),
                      basedOn,
                      action,
                      null,
                      reason,
                      null,
                      null,
                      null,
                      null,
                      null
                  );
        }

        String supersedesThreadKey = null;
        if (action == ComposedFeedbackUnit.Action.SUPERSEDE) {
            supersedesThreadKey = text(unit, "supersedesThreadKey", ComposedFeedbackUnit.MAX_THREAD_KEY_LENGTH);
            // A supersession target the composer was never shown is a target it invented, and acting on
            // it would let a model retire a message it cannot have read.
            if (supersedesThreadKey == null || !preparedThreadKeys.contains(supersedesThreadKey)) {
                log.warn(
                    "Composed unit names a supersession target that was not staged: channel={}, practice={}",
                    channel,
                    practiceSlug
                );
                return null;
            }
        }

        String title = text(unit, "title", ComposedFeedbackUnit.MAX_TITLE_LENGTH);
        if (title == null) {
            return null;
        }

        if (channel == FeedbackChannel.IN_CHAT) {
            ComposedFeedbackUnit.ConversationBrief brief = conversationOf(unit);
            return brief == null
                ? null
                : new ComposedFeedbackUnit(
                      channel,
                      normalizeSlug(practiceSlug),
                      basedOn,
                      action,
                      supersedesThreadKey,
                      null,
                      title,
                      null,
                      null,
                      brief,
                      null
                  );
        }

        // Sanitised on the way in, not on the way out: this text is stored and then read verbatim by a
        // developer, and the same sanitiser already guards every other student-facing body.
        String body = StudentTextSanitizer.sanitize(text(unit, "body", ComposedFeedbackUnit.MAX_BODY_LENGTH));
        String nextStep = StudentTextSanitizer.sanitize(
            text(unit, "nextStep", ComposedFeedbackUnit.MAX_NEXT_STEP_LENGTH)
        );
        if (body == null || nextStep == null) {
            return null;
        }

        ComposedFeedbackUnit.ResolvedAnchor anchor = null;
        if (channel == FeedbackChannel.IN_CONTEXT) {
            anchor = resolveAnchor(unit.get("anchor"), observations);
            if (anchor == null) {
                log.warn("Composed IN_CONTEXT unit has no placeable anchor: practice={}", practiceSlug);
                return null;
            }
        } else if (unit.get("anchor") != null && !unit.get("anchor").isNull()) {
            // An anchor on a longitudinal lane is a category error: those surfaces are not on the diff,
            // and a unit that thinks it is anchored was written at the wrong level.
            log.warn("Composed {} unit carries an anchor, which only IN_CONTEXT may have", channel);
            return null;
        }

        ComposedFeedbackUnit composed = new ComposedFeedbackUnit(
            channel,
            normalizeSlug(practiceSlug),
            basedOn,
            action,
            supersedesThreadKey,
            null,
            title,
            body,
            nextStep,
            null,
            anchor
        );
        return composed.isComplete() ? composed : null;
    }

    /**
     * The file, side and line an in-context note goes on — read off the observation's own citation, never
     * off anything the composer typed.
     */
    private static ComposedFeedbackUnit.@Nullable ResolvedAnchor resolveAnchor(
        @Nullable JsonNode anchor,
        Map<String, StagedObservation> observations
    ) {
        if (anchor == null || !anchor.isObject()) {
            return null;
        }
        String observationId = text(anchor, "observationId", 64);
        JsonNode indexNode = anchor.get("citationIndex");
        if (observationId == null || indexNode == null || !indexNode.isIntegralNumber()) {
            return null;
        }
        StagedObservation observation = observations.get(observationId);
        if (observation == null) {
            return null;
        }
        int index = indexNode.asInt();
        if (index < 0 || index >= observation.citations().size()) {
            return null;
        }
        StagedCitation citation = observation.citations().get(index);
        // Both gates, because they refuse different mistakes: an observation nothing in this change can
        // carry a note for, and a citation of one that happens to point outside the diff.
        if (
            !observation.anchorable() ||
            !citation.anchorable() ||
            citation.path() == null ||
            citation.startLine() == null
        ) {
            return null;
        }
        return new ComposedFeedbackUnit.ResolvedAnchor(
            observationId,
            index,
            citation.path(),
            citation.side(),
            citation.startLine(),
            citation.endLine()
        );
    }

    private static Map<String, StagedObservation> readObservations(@Nullable JsonNode node) {
        Map<String, StagedObservation> observations = new LinkedHashMap<>();
        if (node == null || !node.isArray()) {
            return observations;
        }
        for (JsonNode entry : node) {
            String id = text(entry, "id", 64);
            if (id == null) {
                continue;
            }
            List<StagedCitation> citations = new ArrayList<>();
            JsonNode citationNodes = entry.get("citations");
            if (citationNodes != null && citationNodes.isArray()) {
                for (JsonNode citation : citationNodes) {
                    citations.add(
                        new StagedCitation(
                            text(citation, "path", 1024),
                            text(citation, "side", 8),
                            integer(citation, "startLine"),
                            integer(citation, "endLine"),
                            citation.path("anchorable").asBoolean(false)
                        )
                    );
                }
            }
            observations.put(id, new StagedObservation(entry.path("anchorable").asBoolean(false), citations));
        }
        return observations;
    }

    private static Set<String> readThreadKeys(@Nullable JsonNode node) {
        return Set.copyOf(strings(node));
    }

    private static List<String> strings(@Nullable JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode entry : node) {
            if (entry.isString() && !entry.asString().strip().isEmpty()) {
                values.add(entry.asString().strip());
            }
        }
        return List.copyOf(values);
    }

    private static ComposedFeedbackUnit.@Nullable ConversationBrief conversationOf(JsonNode unit) {
        JsonNode conversation = unit.get("conversation");
        if (conversation == null || !conversation.isObject()) {
            return null;
        }
        String opener = StudentTextSanitizer.sanitize(
            text(conversation, "opener", ComposedFeedbackUnit.MAX_NEXT_STEP_LENGTH)
        );
        String evidence = StudentTextSanitizer.sanitize(
            text(conversation, "evidence", ComposedFeedbackUnit.MAX_EVIDENCE_LENGTH)
        );
        String target = StudentTextSanitizer.sanitize(
            text(conversation, "target", ComposedFeedbackUnit.MAX_NEXT_STEP_LENGTH)
        );
        if (opener == null || evidence == null || target == null) {
            return null;
        }
        return new ComposedFeedbackUnit.ConversationBrief(opener, evidence, target);
    }

    private static @Nullable FeedbackChannel channelOf(JsonNode unit) {
        String value = text(unit, "channel", 32);
        if (value == null) {
            return null;
        }
        for (FeedbackChannel channel : FeedbackChannel.values()) {
            if (channel.name().equalsIgnoreCase(value)) {
                return channel;
            }
        }
        return null;
    }

    private static ComposedFeedbackUnit.@Nullable Action actionOf(JsonNode unit) {
        String value = text(unit, "action", 32);
        if (value == null) {
            return null;
        }
        for (ComposedFeedbackUnit.Action action : ComposedFeedbackUnit.Action.values()) {
            if (action.name().equalsIgnoreCase(value)) {
                return action;
            }
        }
        return null;
    }

    private static ComposedFeedbackUnit.@Nullable WithholdReason withholdReasonOf(JsonNode unit) {
        String value = text(unit, "withholdReason", 32);
        if (value == null) {
            return null;
        }
        for (ComposedFeedbackUnit.WithholdReason reason : ComposedFeedbackUnit.WithholdReason.values()) {
            if (reason.name().equalsIgnoreCase(value)) {
                return reason;
            }
        }
        return null;
    }

    private static String normalizeSlug(String practiceSlug) {
        return practiceSlug.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static @Nullable Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isIntegralNumber() ? null : value.asInt();
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

    /** One observation as the composer was shown it — only what deciding an anchor needs. */
    private record StagedObservation(boolean anchorable, List<StagedCitation> citations) {}

    private record StagedCitation(
        @Nullable String path,
        @Nullable String side,
        @Nullable Integer startLine,
        @Nullable Integer endLine,
        boolean anchorable
    ) {}
}
