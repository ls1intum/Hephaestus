package de.tum.cit.aet.hephaestus.practices.feedback;

import org.jspecify.annotations.Nullable;

/** Stores and reads the composed headline, guidance and next step. */
public final class PracticePageFeedbackBody {

    static final String HEADLINE_PREFIX = "### ";
    static final String NEXT_STEP_PREFIX = "**Try next:** ";

    private PracticePageFeedbackBody() {}

    public static String render(String headline, String message, String nextStep) {
        return (HEADLINE_PREFIX
                + oneLine(headline)
                + "\n\n"
                + message.strip()
                + "\n\n"
                + NEXT_STEP_PREFIX
                + oneLine(nextStep));
    }

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

    private static String oneLine(String text) {
        return text.strip().replace('\r', ' ').replace('\n', ' ').strip();
    }
}
