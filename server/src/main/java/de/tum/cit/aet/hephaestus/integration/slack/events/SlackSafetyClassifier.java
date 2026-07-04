package de.tum.cit.aet.hephaestus.integration.slack.events;

/**
 * Message-classification seam for inbound mentor DMs. Before a message drives a mentor turn,
 * {@link SlackMentorService#handleDm} asks this how to treat it; a non-{@code OK} verdict short-circuits the turn
 * and posts a fixed canned response instead of mentoring.
 *
 * <p><strong>This seam does not, by itself, provide safety or crisis detection.</strong> The shipped default,
 * {@link ObviousAbuseFastPathSlackSafetyClassifier}, is only an obvious-abuse keyword fast-path that short-circuits
 * on a few unambiguous English cues; it cannot reliably detect self-harm, crisis, or harassment (paraphrase,
 * other languages, and obfuscation all pass through it). Genuine crisis / self-harm detection is an unsolved
 * problem here and must be supplied by a model-backed moderation/classification bean, which replaces the default
 * through {@code @ConditionalOnMissingBean}. The seam exists so the mentor flow never embeds this logic inline and
 * so that better classification can be dropped in without touching the flow — an {@code OK} verdict is a
 * "no cheap signal", NOT an assertion that the message is safe.
 */
public interface SlackSafetyClassifier {
    /** How to treat this message: answer normally, or divert with a canned response. */
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
     * @param category how to treat the message
     * @param cannedResponse the canned reply to post for a non-{@code OK} category, or {@code null} for {@code OK}
     */
    record Verdict(Category category, String cannedResponse) {
        public boolean safeToMentor() {
            return category == Category.OK;
        }
    }

    /** Classify one inbound DM body. Never returns {@code null}. */
    Verdict classify(String text);
}
