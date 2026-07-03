package de.tum.cit.aet.hephaestus.integration.slack.events;

/**
 * Duty-of-care classifier seam for inbound mentor DMs (S9). Before a message drives a mentor turn,
 * {@link SlackMentorService#handleDm} asks this whether it is safe to answer as a coding mentor. A non-{@code OK}
 * verdict short-circuits the turn and posts a fixed, safe canned response instead.
 *
 * <p>The default {@link HeuristicSlackSafetyClassifier} is a conservative keyword heuristic; a richer
 * model-backed classifier can replace it by providing an alternative bean (the default is
 * {@code @ConditionalOnMissingBean}). The point of the seam is that the mentor flow never has to embed
 * crisis/harassment handling inline.
 */
public interface SlackSafetyClassifier {
    /** What kind of message this is, from a duty-of-care standpoint. */
    enum Category {
        /** Ordinary in-scope message — answer normally. */
        OK,
        /** Signals of self-harm or crisis — respond with support resources, do not mentor. */
        SELF_HARM,
        /** Abusive/harassing content — decline and de-escalate. */
        HARASSMENT,
        /** Clearly outside a coding mentor's remit — politely redirect. */
        OUT_OF_SCOPE,
    }

    /**
     * @param category the duty-of-care classification
     * @param cannedResponse the safe reply to post for a non-{@code OK} category, or {@code null} for {@code OK}
     */
    record Verdict(Category category, String cannedResponse) {
        public boolean safeToMentor() {
            return category == Category.OK;
        }
    }

    /** Classify one inbound DM body. Never returns {@code null}. */
    Verdict classify(String text);
}
