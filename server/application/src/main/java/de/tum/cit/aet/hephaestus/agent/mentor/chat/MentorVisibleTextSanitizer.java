package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import java.util.Locale;

public final class MentorVisibleTextSanitizer {

    private MentorVisibleTextSanitizer() {}

    public static boolean isLeakedInternalAnalysis(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.stripLeading().toLowerCase(Locale.ROOT);
        return normalized.matches(
            "^(user (wants|asks|is asking|asked|said)|we need to|need to|i need to|allowed paths:|use function fetch_context|according to (the )?(instructions|guidelines)).*"
        );
    }
}
