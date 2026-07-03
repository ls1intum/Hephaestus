package de.tum.cit.aet.hephaestus.integration.slack.mentor;

import static com.slack.api.model.block.Blocks.actions;
import static com.slack.api.model.block.Blocks.context;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.asContextElements;
import static com.slack.api.model.block.element.BlockElements.asElements;
import static com.slack.api.model.block.element.BlockElements.button;

import com.slack.api.model.block.LayoutBlock;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Assembles the interactive blocks the mentor attaches to a turn: a binary "was this helpful?" feedback-button
 * pair, and a three-way uptake block for a piece of delivered feedback.
 *
 * <p>These are valid Bolt {@link LayoutBlock}s (a {@code context} label + an {@code actions} row of buttons) with a
 * plain-text fallback carried by the surrounding message, so they degrade gracefully on a client that cannot render
 * the actions. Every button carries a compact-JSON {@code value} that the interactivity handler parses back:
 * {@code {"ts":"…","fid":"…"}} for a thumb (the turn's message {@code ts} plus the optional bound feedback id) and
 * {@code {"fid":"…"}} for an uptake button.
 *
 * <p><strong>Separation of concerns (the correctness trap).</strong> The thumbs
 * ({@link #ACTION_TURN_HELPFUL}/{@link #ACTION_TURN_UNHELPFUL}) are a satisfaction signal and route ONLY to
 * {@code mentor_turn_rating}. The uptake buttons ({@link #ACTION_UPTAKE_ADDRESSED} /
 * {@link #ACTION_UPTAKE_NOT_APPLICABLE} / {@link #ACTION_UPTAKE_DISPUTED}) are the ONLY path that writes a
 * {@code Reaction}. A thumb never writes {@code ADDRESSED}.
 */
public final class SlackFeedbackBlocks {

    /** Thumbs-up on a turn → a HELPFUL {@code mentor_turn_rating}. */
    public static final String ACTION_TURN_HELPFUL = "turn_helpful";
    /** Thumbs-down on a turn → an UNHELPFUL {@code mentor_turn_rating} (opens a dispute modal on a bound turn). */
    public static final String ACTION_TURN_UNHELPFUL = "turn_unhelpful";

    /** Uptake "Acted on it" → a {@code Reaction} ADDRESSED. */
    public static final String ACTION_UPTAKE_ADDRESSED = "uptake_addressed";
    /** Uptake "Doesn't apply" → a {@code Reaction} NOT_APPLICABLE. */
    public static final String ACTION_UPTAKE_NOT_APPLICABLE = "uptake_not_applicable";
    /** Uptake "Disagree" → opens a dispute modal whose submission writes a {@code Reaction} DISPUTED. */
    public static final String ACTION_UPTAKE_DISPUTED = "uptake_disputed";

    private SlackFeedbackBlocks() {}

    /**
     * The binary feedback buttons for a completed turn. {@code messageTs} is the {@code ts} of the streamed reply;
     * {@code feedbackId} is the delivered conversational feedback the turn raised, or {@code null} for an unbound
     * turn (a pure satisfaction thumb with no dispute path).
     */
    public static List<LayoutBlock> feedbackButtons(String messageTs, @Nullable UUID feedbackId) {
        String value = turnValue(messageTs, feedbackId);
        return List.of(
            context(c -> c.elements(asContextElements(markdownText("_Was this helpful?_")))),
            actions(a ->
                a.elements(
                    asElements(
                        button(b -> b.text(plainText("👍 Helpful")).actionId(ACTION_TURN_HELPFUL).value(value)),
                        button(b -> b.text(plainText("👎 Not helpful")).actionId(ACTION_TURN_UNHELPFUL).value(value))
                    )
                )
            )
        );
    }

    /**
     * The three-way uptake block for one delivered piece of feedback. {@code feedbackId} is nullable: an unbound
     * block still renders (the handler no-ops a reaction it cannot anchor), so the same builder serves a turn whose
     * feedback id is not yet plumbed through.
     */
    public static List<LayoutBlock> uptakeBlock(@Nullable UUID feedbackId) {
        String value = fidValue(feedbackId);
        return List.of(
            context(c -> c.elements(asContextElements(markdownText("_Did you act on this?_")))),
            actions(a ->
                a.elements(
                    asElements(
                        button(b ->
                            b
                                .text(plainText("Acted on it"))
                                .actionId(ACTION_UPTAKE_ADDRESSED)
                                .value(value)
                                .style("primary")
                        ),
                        button(b ->
                            b.text(plainText("Doesn't apply")).actionId(ACTION_UPTAKE_NOT_APPLICABLE).value(value)
                        ),
                        button(b ->
                            b.text(plainText("Disagree")).actionId(ACTION_UPTAKE_DISPUTED).value(value).style("danger")
                        )
                    )
                )
            )
        );
    }

    /** Compact JSON {@code {"ts":"…"[,"fid":"…"]}} — safe manual encoding (ts and uuid contain no JSON metachars). */
    public static String turnValue(String messageTs, @Nullable UUID feedbackId) {
        StringBuilder sb = new StringBuilder("{\"ts\":\"").append(messageTs).append('"');
        if (feedbackId != null) {
            sb.append(",\"fid\":\"").append(feedbackId).append('"');
        }
        return sb.append('}').toString();
    }

    /** Compact JSON {@code {"fid":"…"}} (or {@code {}} when unbound). */
    public static String fidValue(@Nullable UUID feedbackId) {
        return feedbackId == null ? "{}" : "{\"fid\":\"" + feedbackId + "\"}";
    }
}
