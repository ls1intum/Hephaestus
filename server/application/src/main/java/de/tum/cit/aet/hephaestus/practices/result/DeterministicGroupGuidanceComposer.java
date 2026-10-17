package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeStandingDTO;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

final class DeterministicGroupGuidanceComposer {

    private static final int MAX_REMINDER_LENGTH = 180;

    private DeterministicGroupGuidanceComposer() {}

    static String compose(PracticeStanding status, List<PracticeStandingDTO> cards) {
        List<PracticeStandingDTO> strengths = cards.stream()
                .filter(card -> card.standing() == PracticeStanding.STRENGTH
                        || (card.standing() == PracticeStanding.MIXED
                                && !card.strengths().isEmpty()))
                .toList();
        List<PracticeStandingDTO> gaps = cards.stream()
                .filter(card -> card.standing() != PracticeStanding.STRENGTH)
                .toList();

        StringBuilder summary = new StringBuilder();
        switch (status) {
            case STRENGTH ->
                summary.append(
                                strengths.size() == 1
                                        ? "Your recent feedback shows a strength in "
                                        : "Your recent feedback shows strengths in ")
                        .append(nameList(strengths))
                        .append(strengths.size() == 1 ? ". Keep building on it." : ". Keep building on them.");
            case DEVELOPING -> appendNextPractices(summary, gaps);
            case MIXED -> {
                if (strengths.isEmpty()) {
                    appendNextPractices(summary, gaps);
                } else if (samePractices(strengths, gaps)) {
                    summary.append("Your recent feedback is mixed in ")
                            .append(nameList(gaps))
                            .append(", with both strengths and room to grow.");
                } else {
                    summary.append("Your recent feedback shows a strength in ")
                            .append(nameList(strengths))
                            .append(". Next, work on ")
                            .append(nameList(gaps))
                            .append(".");
                }
            }
            case NOT_OBSERVED, NO_OPPORTUNITY ->
                throw new IllegalStateException("Non-verdict statuses are handled before composing guidance");
        }

        if (!gaps.isEmpty()) {
            appendCatalogReminder(summary, gaps.get(0));
        } else if (!strengths.isEmpty()) {
            appendCatalogReminder(summary, strengths.get(0));
        }
        return summary.toString();
    }

    private static void appendNextPractices(StringBuilder summary, List<PracticeStandingDTO> gaps) {
        summary.append("Your recent feedback suggests that ")
                .append(nameList(gaps))
                .append(gaps.size() == 1 ? " is the next practice to work on." : " are the next practices to work on.");
    }

    private static void appendCatalogReminder(StringBuilder summary, PracticeStandingDTO practice) {
        String exemplar = nonBlank(practice.whatGoodLooksLike());
        if (exemplar != null) {
            summary.append(" What good looks like: ").append(conciseReminder(exemplar));
            return;
        }
        String rationale = nonBlank(practice.whyItMatters());
        if (rationale != null) {
            summary.append(" Why it matters: ").append(conciseReminder(rationale));
        }
    }

    private static boolean samePractices(List<PracticeStandingDTO> strengths, List<PracticeStandingDTO> gaps) {
        return (!strengths.isEmpty()
                && strengths.stream()
                        .map(PracticeStandingDTO::slug)
                        .toList()
                        .equals(gaps.stream().map(PracticeStandingDTO::slug).toList()));
    }

    private static String nameList(List<PracticeStandingDTO> cards) {
        List<String> names = cards.stream()
                .map(PracticeStandingDTO::name)
                .filter(Objects::nonNull)
                .map(name -> "“" + name + "”")
                .distinct()
                .toList();
        return switch (names.size()) {
            case 0 -> "this group";
            case 1 -> names.get(0);
            case 2 -> names.get(0) + " and " + names.get(1);
            default -> names.get(0) + ", " + names.get(1) + ", and other practices";
        };
    }

    private static @Nullable String nonBlank(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String conciseReminder(String value) {
        String singleLine = value.replaceAll("\\s+", " ").trim();
        if (singleLine.length() > MAX_REMINDER_LENGTH) {
            int wordBoundary = singleLine.lastIndexOf(' ', MAX_REMINDER_LENGTH - 1);
            int end = wordBoundary >= MAX_REMINDER_LENGTH / 2 ? wordBoundary : MAX_REMINDER_LENGTH - 1;
            return singleLine.substring(0, end).stripTrailing() + "…";
        }
        char last = singleLine.charAt(singleLine.length() - 1);
        return last == '.' || last == '!' || last == '?' ? singleLine : singleLine + ".";
    }
}
