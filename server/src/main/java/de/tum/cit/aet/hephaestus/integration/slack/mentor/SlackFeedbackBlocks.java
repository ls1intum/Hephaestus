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

/**
 * Assembles the interactive blocks the mentor attaches to a completed turn: a binary "was this helpful?"
 * feedback-button pair.
 *
 * <p>These are valid Bolt {@link LayoutBlock}s (a {@code context} label + an {@code actions} row of buttons) with a
 * plain-text fallback carried by the surrounding message, so they degrade gracefully on a client that cannot render
 * the actions. Each button carries a compact-JSON {@code value} ({@code {"ts":"…"}}, the turn's message {@code ts})
 * that the interactivity handler parses back to append a {@code mentor_turn_rating}.
 */
public final class SlackFeedbackBlocks {

    /** Thumbs-up on a turn → a HELPFUL {@code mentor_turn_rating}. */
    public static final String ACTION_TURN_HELPFUL = "turn_helpful";
    /** Thumbs-down on a turn → an UNHELPFUL {@code mentor_turn_rating}. */
    public static final String ACTION_TURN_UNHELPFUL = "turn_unhelpful";

    private SlackFeedbackBlocks() {}

    /**
     * The binary feedback buttons for a completed turn. {@code messageTs} is the {@code ts} of the streamed reply,
     * carried in each button's value so a click records a rating anchored to that turn.
     */
    public static List<LayoutBlock> feedbackButtons(String messageTs) {
        String value = turnValue(messageTs);
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

    /** Compact JSON {@code {"ts":"…"}} — safe manual encoding (a Slack ts contains no JSON metachars). */
    public static String turnValue(String messageTs) {
        return "{\"ts\":\"" + messageTs + "\"}";
    }
}
