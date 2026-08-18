package de.tum.cit.aet.hephaestus.agent.handler.inapp;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One process-level message the composition stage wrote, before the server has decided whether the
 * recipient may see it.
 *
 * <p>It names a practice, not a set of observations. The model reads a bounded, sanitised window of the
 * recipient's record and cannot know observation ids; the server resolves the practice to the
 * recipient's own measurements, so what a message is evidenced by is never the model's to assert.
 *
 * @param practiceSlug the practice whose pattern this message is about
 * @param title        a short headline naming the pattern, not the person
 * @param body         the process-level message; the developer reads this verbatim
 * @param nextStep     one habit to try next time — the feed-forward half, which is never optional
 * @param supersedesThreadKey the continuity key of a queued card this one is meant to replace, or
 *     {@code null} when the composer wrote it to stand on its own. It is the composer's <em>intent</em>
 *     and nothing more: whether the replacement can actually happen depends on whether that card has been
 *     read by the time the server acts, which is not knowable when the words are written
 */
public record ComposedInAppMessage(
    String practiceSlug,
    String title,
    String body,
    String nextStep,
    @Nullable String supersedesThreadKey
) {
    /** Guards on the ledger's own column widths, so a message can never be truncated after it is admitted. */
    public static final int MAX_TITLE_LENGTH = 255;

    public static final int MAX_BODY_LENGTH = 8_000;
    public static final int MAX_NEXT_STEP_LENGTH = 2_000;

    public ComposedInAppMessage {
        Objects.requireNonNull(practiceSlug, "practiceSlug");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(nextStep, "nextStep");
    }

    /** Whether the message says enough to be feedback at all: something to read, and something to do. */
    public boolean isComplete() {
        return !practiceSlug.isBlank() && !title.isBlank() && !body.isBlank() && !nextStep.isBlank();
    }
}
