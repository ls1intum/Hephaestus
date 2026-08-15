package de.tum.cit.aet.hephaestus.agent.handler.reflection;

import java.util.Objects;

/**
 * One process-level message the composition stage wrote, before the server has decided whether the
 * recipient may see it.
 *
 * <p>Deliberately NOT a measurement: it carries no {@code presence}, no {@code assessment}, no
 * {@code severity} and no {@code confidence}, and it cites no artifact location. Those belong to an
 * observation, which is the measurement; this is the intervention. Keeping the two shapes structurally
 * different is what stops a composed message from being read back as evidence for itself.
 *
 * <p>It names a practice, not a set of observations. The model reads a bounded, sanitised window of the
 * recipient's record and cannot know observation ids; the server resolves the practice to the
 * recipient's own measurements, so what a message is evidenced by is never the model's to assert.
 *
 * @param practiceSlug the practice whose pattern this message is about
 * @param title        a short headline naming the pattern, not the person
 * @param body         the process-level message; the developer reads this verbatim
 * @param nextStep     one habit to try next time — the feed-forward half, which is never optional
 */
public record ComposedReflectionMessage(String practiceSlug, String title, String body, String nextStep) {
    /** Guards on the ledger's own column widths, so a message can never be truncated after it is admitted. */
    public static final int MAX_TITLE_LENGTH = 255;

    public static final int MAX_BODY_LENGTH = 8_000;
    public static final int MAX_NEXT_STEP_LENGTH = 2_000;

    public ComposedReflectionMessage {
        Objects.requireNonNull(practiceSlug, "practiceSlug");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(nextStep, "nextStep");
    }

    /**
     * Whether the message says enough to be feedback at all: something to read, and something to do.
     * Both halves are required — evidence without a next step is a verdict, and a next step without the
     * pattern it follows from is an instruction.
     */
    public boolean isComplete() {
        return !practiceSlug.isBlank() && !title.isBlank() && !body.isBlank() && !nextStep.isBlank();
    }
}
