package de.tum.cit.aet.hephaestus.agent.handler.composition;

import de.tum.cit.aet.hephaestus.practices.feedback.DeveloperTextSanitizer;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class FeedbackCompositionResultParser {

    private static final Logger log = LoggerFactory.getLogger(FeedbackCompositionResultParser.class);

    public static final String OUTPUT_KEY = "feedback";

    /**
     * A bound on how much composed prose one cycle can offer, before each lane's per-recipient cap
     * applies. Not a policy — a guard against a runaway turn filling the ledger.
     */
    static final int MAX_UNITS = 30;

    private static final int MAX_PRACTICE_SLUG_LENGTH = 128;

    /** A ceiling on the payload, not on the opening: the composer clamps the lead to its own budget. */
    private static final int MAX_LEAD_LENGTH = 4_000;

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
        Set<PreparedTarget> preparedTargets = readPreparedTargets(payload.get("preparedTargets"));

        List<ComposedFeedbackUnit> parsed = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode unit : units) {
            if (parsed.size() >= MAX_UNITS) {
                log.warn("Composition stage reported more than {} units; the tail was ignored", MAX_UNITS);
                break;
            }
            ComposedFeedbackUnit composed = read(unit, observations, preparedTargets);
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

    public List<ComposedFeedbackUnit> parse(@Nullable JsonNode jobOutput, FeedbackChannel channel) {
        return parse(jobOutput)
            .stream()
            .filter(unit -> unit.channel() == channel)
            .toList();
    }

    private static @Nullable ComposedFeedbackUnit read(
        JsonNode unit,
        Map<String, StagedObservation> observations,
        Set<PreparedTarget> preparedTargets
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
            return null;
        }
        String normalizedPracticeSlug = normalizeSlug(practiceSlug);
        boolean grounded = basedOn.stream().allMatch(observations::containsKey);
        boolean ownsEvidence = basedOn
            .stream()
            .map(observations::get)
            .filter(Objects::nonNull)
            .anyMatch(observation -> normalizedPracticeSlug.equals(observation.practiceSlug()));
        if (!grounded || !ownsEvidence) {
            log.warn(
                "Composed unit is not grounded in admitted evidence for its practice: channel={}, practice={}",
                channel,
                practiceSlug
            );
            return null;
        }

        if (action == ComposedFeedbackUnit.Action.WITHHOLD) {
            ComposedFeedbackUnit.WithholdReason reason = withholdReasonOf(unit);
            return reason == null
                ? null
                : new ComposedFeedbackUnit(
                      channel,
                      normalizedPracticeSlug,
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
            if (
                supersedesThreadKey == null ||
                !preparedTargets.contains(new PreparedTarget(supersedesThreadKey, channel, normalizedPracticeSlug))
            ) {
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
            ComposedFeedbackUnit.ConversationBrief brief = notesOf(unit);
            return brief == null
                ? null
                : new ComposedFeedbackUnit(
                      channel,
                      normalizedPracticeSlug,
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

        String nextStep = DeveloperTextSanitizer.sanitize(
            text(unit, "nextStep", ComposedFeedbackUnit.MAX_NEXT_STEP_LENGTH)
        );
        if (nextStep == null) return null;

        String body = null;
        ComposedFeedbackUnit.InContextPlacement placement = null;
        if (channel == FeedbackChannel.IN_CONTEXT) {
            if (unit.hasNonNull("body")) return null;
            placement = resolvePlacement(unit.get("placement"), observations, basedOn, normalizedPracticeSlug);
            if (placement == null) {
                log.warn("Composed IN_CONTEXT unit has no valid placement: practice={}", practiceSlug);
                return null;
            }
        } else if (unit.get("placement") != null && !unit.get("placement").isNull()) {
            log.warn("Composed {} unit carries an anchor, which only IN_CONTEXT may have", channel);
            return null;
        }
        if (channel == FeedbackChannel.IN_APP) {
            body = DeveloperTextSanitizer.sanitize(text(unit, "body", ComposedFeedbackUnit.MAX_BODY_LENGTH));
            if (body == null) return null;
        }

        ComposedFeedbackUnit composed = new ComposedFeedbackUnit(
            channel,
            normalizedPracticeSlug,
            basedOn,
            action,
            supersedesThreadKey,
            null,
            title,
            body,
            nextStep,
            null,
            placement
        );
        return composed.isComplete() ? composed : null;
    }

    private static ComposedFeedbackUnit.@Nullable InContextPlacement resolvePlacement(
        @Nullable JsonNode placement,
        Map<String, StagedObservation> observations,
        List<String> basedOn,
        String practiceSlug
    ) {
        if (placement == null || !placement.isObject()) return null;
        String kind = text(placement, "kind", 16);
        if ("ARTIFACT".equals(kind)) {
            if (placement.hasNonNull("observationId") || placement.hasNonNull("citationIndex")) return null;
            boolean grounded = basedOn
                .stream()
                .map(observations::get)
                .anyMatch(observation -> observation != null && practiceSlug.equals(observation.practiceSlug()));
            if (!grounded) return null;
            return new ComposedFeedbackUnit.InContextPlacement(
                ComposedFeedbackUnit.InContextPlacement.PlacementKind.ARTIFACT,
                null
            );
        }
        if (!"DIFF".equals(kind)) return null;
        ComposedFeedbackUnit.ResolvedAnchor anchor = resolveAnchor(placement, observations);
        return anchor == null
            ? null
            : new ComposedFeedbackUnit.InContextPlacement(
                  ComposedFeedbackUnit.InContextPlacement.PlacementKind.DIFF,
                  anchor
              );
    }

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

    /** How the review opens, in the composer's words; null when it wrote none. */
    @Nullable
    public String lead(@Nullable JsonNode jobOutput) {
        return jobOutput == null ? null : text(jobOutput.path("feedback"), "lead", MAX_LEAD_LENGTH);
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
            observations.put(
                id,
                new StagedObservation(
                    normalizedSlug(text(entry, "practiceSlug", MAX_PRACTICE_SLUG_LENGTH)),
                    entry.path("anchorable").asBoolean(false),
                    citations
                )
            );
        }
        return observations;
    }

    private static Set<PreparedTarget> readPreparedTargets(@Nullable JsonNode node) {
        if (node == null || !node.isArray()) return Set.of();
        Set<PreparedTarget> targets = new LinkedHashSet<>();
        for (JsonNode entry : node) {
            String threadKey = text(entry, "threadKey", ComposedFeedbackUnit.MAX_THREAD_KEY_LENGTH);
            FeedbackChannel channel = channelOf(entry);
            String practiceSlug = normalizedSlug(text(entry, "practiceSlug", MAX_PRACTICE_SLUG_LENGTH));
            if (threadKey != null && channel != null && practiceSlug != null) {
                targets.add(new PreparedTarget(threadKey, channel, practiceSlug));
            }
        }
        return Set.copyOf(targets);
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

    private static ComposedFeedbackUnit.@Nullable ConversationBrief notesOf(JsonNode unit) {
        JsonNode notes = unit.get("notes");
        if (notes == null || !notes.isObject()) {
            return null;
        }
        String situation = note(notes, "situation", ComposedFeedbackUnit.MAX_SITUATION_LENGTH);
        String capability = note(notes, "capability", ComposedFeedbackUnit.MAX_AIM_LENGTH);
        String evidenceSummary = note(notes, "evidenceSummary", ComposedFeedbackUnit.MAX_EVIDENCE_LENGTH);
        String inConversationSignal = note(notes, "inConversationSignal", ComposedFeedbackUnit.MAX_AIM_LENGTH);
        if (situation == null || capability == null || evidenceSummary == null || inConversationSignal == null) {
            return null;
        }
        return new ComposedFeedbackUnit.ConversationBrief(
            situation,
            capability,
            evidenceSummary,
            inConversationSignal,
            note(notes, "alreadySaid", ComposedFeedbackUnit.MAX_AIM_LENGTH)
        );
    }

    private static @Nullable String note(JsonNode notes, String field, int maxLength) {
        String sanitized = DeveloperTextSanitizer.sanitize(text(notes, field, maxLength));
        return sanitized.isBlank() ? null : sanitized;
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

    private static @Nullable String normalizedSlug(@Nullable String practiceSlug) {
        return practiceSlug == null ? null : normalizeSlug(practiceSlug);
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
        return text.length() <= maxLength ? text : null;
    }

    private record StagedObservation(
        @Nullable String practiceSlug,
        boolean anchorable,
        List<StagedCitation> citations
    ) {}

    private record PreparedTarget(String threadKey, FeedbackChannel channel, String practiceSlug) {}

    private record StagedCitation(
        @Nullable String path,
        @Nullable String side,
        @Nullable Integer startLine,
        @Nullable Integer endLine,
        boolean anchorable
    ) {}
}
