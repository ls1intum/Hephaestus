package de.tum.cit.aet.hephaestus.agent.handler.composition;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One thing the composition stage decided to say — or decided not to say — on one channel, before the
 * server has decided whether the recipient may see it.
 *
 * <p><b>It carries no verdict.</b> No presence, no assessment, no severity, no confidence, and no
 * citation the composer typed: an intervention that could carry a verdict would eventually be read back
 * as one. What it does carry is what it rests on ({@link #basedOn}), which is the composer's own account
 * of which measurements it used — the server still resolves those to rows itself, because evidence a
 * model asserts about itself is not evidence.
 *
 * <p><b>Which fields are present is a function of the channel, and that is the design rather than an
 * accident of the schema.</b> The three surfaces answer three different questions, so they are not the
 * same message rendered three ways.
 *
 * @param basedOn what this rests on: ids of this run's observations, and/or {@code prior:<practiceSlug>}
 *     for a claim that rests on the record rather than on this run — which is how a message about
 *     something that got <em>fixed</em> can exist at all, since nothing was measured for it this time
 * @param body the words, read verbatim, for {@link FeedbackChannel#IN_CONTEXT} and
 *     {@link FeedbackChannel#IN_APP}. Null on the conversation lane, where {@link #conversation}
 *     carries the move instead
 * @param anchor where an in-context note goes, already resolved against the observation's own citation
 */
public record ComposedFeedbackUnit(
    FeedbackChannel channel,
    String practiceSlug,
    List<String> basedOn,
    Action action,
    @Nullable String supersedesThreadKey,
    @Nullable WithholdReason withholdReason,
    @Nullable String title,
    @Nullable String body,
    @Nullable String nextStep,
    @Nullable ConversationBrief conversation,
    @Nullable ResolvedAnchor anchor
) {
    /** Guards on the ledger's own column widths, so a unit can never be truncated after it is admitted. */
    public static final int MAX_TITLE_LENGTH = 255;

    public static final int MAX_BODY_LENGTH = 8_000;
    public static final int MAX_NEXT_STEP_LENGTH = 2_000;
    public static final int MAX_EVIDENCE_LENGTH = 4_000;
    public static final int MAX_THREAD_KEY_LENGTH = 64;

    public ComposedFeedbackUnit {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(practiceSlug, "practiceSlug");
        Objects.requireNonNull(action, "action");
        basedOn = List.copyOf(basedOn);
    }

    /** What this unit does to the recipient's queue. */
    public enum Action {
        /** Add a message. */
        NEW,
        /** Replace a message that is queued and has not been read; {@link #supersedesThreadKey} says which. */
        SUPERSEDE,
        /** Say nothing, and record why — so silence can be explained rather than look like a failure. */
        WITHHOLD,
    }

    /**
     * Why nothing is being said. Deliberately not
     * {@link de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason}: that enum is a
     * database CHECK constraint and every value in it is a reason <em>the server</em> held something
     * back, while these are the composer's reasons. Mapping one onto the other means widening the
     * constraint, which is a schema change and belongs with the unit that makes it.
     */
    public enum WithholdReason {
        /** Nothing moved at this locus since it was last measured. */
        NO_MATERIAL_CHANGE,
        /** This person has already been told this. */
        ALREADY_SAID,
        /** True, but not worth a message. */
        BELOW_BAR,
    }

    /**
     * The mentor's move, composed now; the mentor's words, still written at the turn.
     *
     * <p>This is where the line sits between what is frozen at composition and what is not, and it is
     * drawn deliberately. {@code ConversationalFeedbackPreparer} wrote a NULL body precisely so that no
     * stale snippet was frozen at preparation time, and that reasoning is right. What is composed here is
     * therefore the <em>question to open with</em>, the <em>evidence to hold back</em> and the
     * <em>target</em> — not a script. The mentor still responds to what the developer actually says, and
     * still decides when to show the evidence, so it keeps the contextual advantage that made a null body
     * the right call; what it no longer has to invent from scratch is the move itself.
     *
     * @param opener a question about how they work, asked before anything is told, so the developer
     *     produces the diagnosis
     * @param evidence what to show once they have answered, and not before
     * @param target what the turn is trying to leave them able to do for themselves
     */
    public record ConversationBrief(String opener, String evidence, String target) {
        public ConversationBrief {
            Objects.requireNonNull(opener, "opener");
            Objects.requireNonNull(evidence, "evidence");
            Objects.requireNonNull(target, "target");
        }
    }

    /**
     * Where an in-context note goes, resolved in Java from the observation's own citation.
     *
     * <p>The composer names an observation and a citation index and nothing else. It never names a file
     * and never names a line, so it cannot invent an anchor — which retires the whole class of failure
     * that grounding checks currently have to catch after the note has already been composed.
     */
    public record ResolvedAnchor(
        String observationId,
        int citationIndex,
        String path,
        @Nullable String side,
        int startLine,
        @Nullable Integer endLine
    ) {
        public ResolvedAnchor {
            Objects.requireNonNull(observationId, "observationId");
            Objects.requireNonNull(path, "path");
        }
    }

    /** Whether the unit says enough to be feedback at all: something to read, and something to do. */
    public boolean isComplete() {
        if (action == Action.WITHHOLD) {
            return withholdReason != null;
        }
        if (title == null || title.isBlank()) {
            return false;
        }
        return channel == FeedbackChannel.IN_CHAT
            ? conversation != null
            : body != null && !body.isBlank() && nextStep != null && !nextStep.isBlank();
    }
}
