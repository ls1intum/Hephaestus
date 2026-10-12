package de.tum.cit.aet.hephaestus.agent.handler.composition;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Complete public bodies with exact support; admission accepts or refuses each body without rewriting it. */
public record ComposedReview(@Nullable Summary summary, List<InlineNote> inline, List<Withheld> withheld) {

    /** Earlier fragment contracts cannot produce fresh public delivery. */
    public static final int CONTRACT_VERSION = 2;

    public static final int MAX_SUMMARY_LENGTH = 8_000;

    public static final int MAX_INLINE_LENGTH = 2_000;

    /** Oversize reviews are refused, never shortened by selecting an arbitrary prefix. */
    public static final int MAX_INLINE_NOTES = 30;

    public ComposedReview {
        inline = List.copyOf(inline);
        withheld = List.copyOf(withheld);
    }

    /** The results some part of the review decided about: said in the summary or a line note, or withheld. */
    public Set<String> decidedResultIds() {
        Set<String> ids = new HashSet<>();
        if (summary != null) ids.addAll(summary.basedOn());
        inline.forEach(note -> ids.addAll(note.basedOn()));
        withheld.forEach(decision -> ids.addAll(decision.basedOn()));
        return Set.copyOf(ids);
    }

    public static ComposedReview empty() {
        return new ComposedReview(null, List.of(), List.of());
    }

    /**
     * One piece of evidence a composed text rests on: a practice result, and optionally one of its problems. On the
     * wire, {@code {practiceResultId, observationId?}}; the problem must belong to that result.
     */
    public record EvidenceRef(
            String practiceResultId, @Nullable String observationId) {
        public EvidenceRef {
            Objects.requireNonNull(practiceResultId, "practiceResultId");
            if (practiceResultId.isBlank() || (observationId != null && observationId.isBlank())) {
                throw new IllegalArgumentException("An evidence reference names its result, and its problem if any");
            }
        }
    }

    /**
     * @param basedOn the results the summary rests on, each once
     * @param observations the problems of those results it names, each once
     */
    public record Summary(String body, List<String> basedOn, List<String> observations) {
        public Summary {
            body = text(body, MAX_SUMMARY_LENGTH);
            basedOn = support(basedOn);
            observations = distinct(observations);
        }

        public Summary(String body, List<String> basedOn) {
            this(body, basedOn, List.of());
        }
    }

    /**
     * The anchor is resolved from a cited problem's citation, never model-authored coordinates.
     *
     * @param observations the problems the note names; the anchored problem is one of them
     */
    public record InlineNote(String body, List<String> basedOn, List<String> observations, ResolvedAnchor anchor) {
        public InlineNote {
            body = text(body, MAX_INLINE_LENGTH);
            Objects.requireNonNull(anchor, "anchor");
            basedOn = support(basedOn);
            observations = distinct(observations);
            if (!basedOn.contains(anchor.practiceResultId()) || !observations.contains(anchor.observationId())) {
                throw new IllegalArgumentException("A line note's anchor must be one of the problems it rests on");
            }
        }
    }

    /** Results the composer decided not to raise on the work, with its reason. */
    public record Withheld(List<String> basedOn, ComposedFeedbackUnit.WithholdReason reason) {
        public Withheld {
            Objects.requireNonNull(reason, "reason");
            basedOn = support(basedOn);
        }
    }

    /** A text as written: never blank and never over its bound, with nothing trimmed from it. */
    private static String text(String body, int maxLength) {
        Objects.requireNonNull(body, "body");
        if (body.isBlank() || body.length() > maxLength) {
            throw new IllegalArgumentException("A composed text is blank or over its " + maxLength + " characters");
        }
        return body;
    }

    /** What a part rests on: at least one result id, each named once, none blank. */
    private static List<String> support(List<String> basedOn) {
        List<String> ids = distinct(basedOn);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("A composed part rests on a non-empty list of distinct result ids");
        }
        return ids;
    }

    private static List<String> distinct(List<String> ids) {
        List<String> copy = List.copyOf(ids);
        if (copy.stream().anyMatch(String::isBlank) || copy.stream().distinct().count() != copy.size()) {
            throw new IllegalArgumentException("A composed part names each piece of evidence once");
        }
        return copy;
    }

    /** A line resolved from one citation of one problem. */
    public record ResolvedAnchor(
            String practiceResultId,
            String observationId,
            int citationIndex,
            String path,
            @Nullable String side,
            int startLine,
            @Nullable Integer endLine) {
        public ResolvedAnchor {
            Objects.requireNonNull(practiceResultId, "practiceResultId");
            Objects.requireNonNull(observationId, "observationId");
            Objects.requireNonNull(path, "path");
        }
    }
}
