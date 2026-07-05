package de.tum.cit.aet.hephaestus.integration.slack.events;

import java.util.List;
import java.util.Locale;

/**
 * Obvious-abuse keyword fast-path implementing the {@link SlackSafetyClassifier} seam. This is NOT a safety or
 * crisis-detection system: it is a narrow substring matcher that only short-circuits on a handful of
 * <em>unambiguous</em> English cues (an explicit self-harm phrase, an explicit slur/threat at the bot). It exists
 * only so the mentor flow has a non-null default and so the most blatant messages get a fixed, non-mentoring
 * reply instead of a code review.
 *
 * <p><strong>Known limits — do not mistake this for safety.</strong> A substring list cannot detect crisis or
 * self-harm: it misses paraphrase ("I don't want to be here anymore"), every non-English phrasing, sarcasm,
 * negation, and obfuscation, and it will false-positive on quoted or technical text. Real crisis / self-harm
 * detection is an <em>unsolved</em> problem here and is deliberately out of scope for this class. When a message
 * is NOT matched, that means nothing about whether it was actually safe — it just means this cheap matcher had no
 * opinion, and the message is mentored normally.
 *
 * <p>Registered as the default classifier in {@link SlackSeamDefaultsConfiguration}. The intended production
 * posture is to replace it with a model-backed moderation/classification bean via that seam; this fast-path is the
 * fallback so the flow is never wired with a null classifier.
 */
public class ObviousAbuseFastPathSlackSafetyClassifier implements SlackSafetyClassifier {

    private static final List<String> SELF_HARM_CUES = List.of(
        "kill myself",
        "killing myself",
        "suicide",
        "suicidal",
        "end my life",
        "want to die",
        "kill me",
        "hurt myself",
        "harming myself",
        "self harm",
        "self-harm",
        "no reason to live"
    );

    private static final List<String> HARASSMENT_CUES = List.of(
        "kill you",
        "kys",
        "i hate you",
        "you're worthless",
        "you are worthless",
        "shut up you",
        "stupid bot"
    );

    static final String SELF_HARM_RESPONSE =
        "It sounds like you may be going through something really hard, and I'm not the right kind of help for " +
        "this — I'm just a coding-practice mentor. Please reach out to someone who can support you right now: in " +
        "many countries you can call or text a crisis line (for example 988 in the US/Canada), or contact your " +
        "local emergency services. You deserve real support, and it's okay to ask for it.";

    static final String HARASSMENT_RESPONSE =
        "I want to keep this a respectful space, so I won't engage with messages like that. When you're ready, ask " +
        "me about your recent pull requests, reviews, or issues and I'll gladly help.";

    static final String OUT_OF_SCOPE_RESPONSE =
        "That's a little outside what I can help with — I'm your Hephaestus practice mentor. Ask me about your " +
        "recent code reviews, pull requests, or issues and I'll dig into them with you.";

    @Override
    public Verdict classify(String text) {
        if (text == null || text.isBlank()) {
            return new Verdict(Category.OK, null);
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, SELF_HARM_CUES)) {
            return new Verdict(Category.SELF_HARM, SELF_HARM_RESPONSE);
        }
        if (containsAny(normalized, HARASSMENT_CUES)) {
            return new Verdict(Category.HARASSMENT, HARASSMENT_RESPONSE);
        }
        return new Verdict(Category.OK, null);
    }

    private static boolean containsAny(String haystack, List<String> needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
