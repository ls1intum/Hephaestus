package de.tum.cit.aet.hephaestus.integration.slack.events;

import java.util.List;
import java.util.Locale;

/**
 * Conservative keyword heuristic implementing the {@link SlackSafetyClassifier} seam (S9). It errs toward answering
 * normally ({@link Category#OK}) and only diverts on unambiguous self-harm or harassment cues, so an ordinary
 * coding question is never blocked. {@link Category#OUT_OF_SCOPE} is left to a richer classifier — the heuristic
 * does not guess at topic scope.
 *
 * <p>Registered as the default {@code @ConditionalOnMissingBean} in {@link SlackSeamDefaultsConfiguration} so a
 * model-backed classifier can replace it without touching the mentor flow. It is NOT a component-scanned bean:
 * {@code @ConditionalOnMissingBean} is only reliable on a {@code @Bean} factory method, not on a scanned
 * {@code @Component} (where evaluation order left the default unregistered and broke context startup).
 */
public class HeuristicSlackSafetyClassifier implements SlackSafetyClassifier {

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
