package de.tum.cit.aet.hephaestus.practices.feedback;

import org.jspecify.annotations.Nullable;

/** The stored Markdown layout for a practice-page headline, body and next step. */
public final class PracticePageFeedbackBody {

    static final String HEADLINE_PREFIX = "### ";
    static final String NEXT_STEP_PREFIX = "**Try next:** ";

    private PracticePageFeedbackBody() {}

    /** The stored body for one composed message. */
    public static String render(String headline, String message, String nextStep) {
        return (HEADLINE_PREFIX
                + oneLine(headline)
                + "\n\n"
                + message.strip()
                + "\n\n"
                + NEXT_STEP_PREFIX
                + oneLine(nextStep));
    }

    /**
     * The headline, or {@code null} for a body this class did not write. Null is the honest answer: a
     * caller that invented one would be putting words in the composer's mouth.
     */
    public static @Nullable String headlineOf(@Nullable String body) {
        if (body == null) {
            return null;
        }
        String first = body.strip().lines().findFirst().orElse("");
        if (!first.startsWith(HEADLINE_PREFIX)) {
            return null;
        }
        String headline = first.substring(HEADLINE_PREFIX.length()).strip();
        return headline.isEmpty() ? null : headline;
    }

    /**
     * The way of working to try next, or {@code null} for a body this class did not write — the same honesty as
     * {@link #headlineOf}. Read off the last line, where {@link #render} puts it.
     */
    public static @Nullable String nextStepOf(@Nullable String body) {
        if (body == null) {
            return null;
        }
        String last = body.strip().lines().reduce((first, second) -> second).orElse("");
        if (!last.startsWith(NEXT_STEP_PREFIX)) {
            return null;
        }
        String nextStep = last.substring(NEXT_STEP_PREFIX.length()).strip();
        return nextStep.isEmpty() ? null : nextStep;
    }

    /**
     * The process-level message on its own: below the headline and above the next step, each dropped
     * only when this class wrote it. A reader gets the three parts separately and never splits the
     * body itself.
     */
    public static String messageOf(@Nullable String body) {
        if (body == null) {
            return "";
        }
        String stripped = body.strip();
        if (headlineOf(stripped) != null) {
            int newline = stripped.indexOf('\n');
            stripped = newline < 0 ? "" : stripped.substring(newline + 1).strip();
        }
        if (nextStepOf(stripped) != null) {
            int newline = stripped.lastIndexOf('\n');
            stripped = newline < 0 ? "" : stripped.substring(0, newline).strip();
        }
        return stripped;
    }

    /** Newlines in a heading would break the layout the reader relies on; collapse them once, here. */
    private static String oneLine(String text) {
        return text.strip().replace('\r', ' ').replace('\n', ' ').strip();
    }
}
