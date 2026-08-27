package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.practices.dto.PracticeAreaStandingDTO;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionPracticeDTO;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Builds the compact developer-facing area summary used until an aggregated guidance snapshot exists. */
final class DeterministicAreaGuidanceComposer {

    /** Keeps catalog prose from turning the compact area card into a full practice description. */
    private static final int MAX_REMINDER_LENGTH = 180;

    private DeterministicAreaGuidanceComposer() {}

    /**
     * Produces a short standing sentence, a concrete focus, and at most one catalog-authored reminder.
     * Detection criteria never enters this method: {@code whatGoodLooksLike}/{@code whyItMatters} are the
     * catalog's dedicated developer-facing layer.
     */
    static @Nullable String compose(PracticeAreaStandingDTO.Standing status, List<ReflectionPracticeDTO> cards) {
        // Without a verdict there is nothing to summarise, and inventing encouragement here would state
        // more than the evidence supports. The surface renders its own reason-specific empty copy.
        if (!PracticeAreaStandingDTO.isVerdict(status)) {
            return null;
        }

        // Partitioned by standing, exactly as the area status is: a practice whose recent evidence already
        // earned it a STRENGTH standing must not be named as the area's next focus merely because older items
        // are still on its card, and the sentence would otherwise contradict the status it explains. A MIXED
        // practice belongs on both sides — that is what makes it mixed.
        //
        // Non-verdict practices are dropped FIRST. Their standing is neither DEVELOPING nor STRENGTH, so a
        // negated filter would put a practice with nothing to say on both sides at once and name it as a
        // strength and a gap in the same sentence.
        List<ReflectionPracticeDTO> verdicts = cards
            .stream()
            .filter(card -> ReflectionPracticeDTO.isVerdict(card.standing()))
            .toList();
        List<ReflectionPracticeDTO> strengths = verdicts
            .stream()
            .filter(card -> card.standing() != ReflectionPracticeDTO.Standing.DEVELOPING)
            .toList();
        List<ReflectionPracticeDTO> gaps = verdicts
            .stream()
            .filter(card -> card.standing() != ReflectionPracticeDTO.Standing.STRENGTH)
            .toList();

        StringBuilder summary = new StringBuilder();
        switch (status) {
            case STRENGTH -> summary
                .append(
                    strengths.size() == 1
                        ? "Your recent feedback shows a strength in "
                        : "Your recent feedback shows strengths in "
                )
                .append(nameList(strengths))
                .append(strengths.size() == 1 ? ". Keep building on it." : ". Keep building on them.");
            case DEVELOPING -> summary
                .append("Your recent feedback points to ")
                .append(nameList(gaps))
                .append(
                    gaps.size() == 1 ? " as the next practice to focus on." : " as the next practices to focus on."
                );
            case MIXED -> {
                if (samePractices(strengths, gaps)) {
                    summary
                        .append("Your recent feedback is mixed in ")
                        .append(nameList(gaps))
                        .append(", with both strengths and room to grow.");
                } else {
                    summary
                        .append("Your recent feedback shows a strength in ")
                        .append(nameList(strengths))
                        .append(". Next, focus on ")
                        .append(nameList(gaps))
                        .append(".");
                }
            }
            case NOT_OBSERVED, NO_OPPORTUNITY -> throw new IllegalStateException(
                "Non-verdict statuses are handled before composing guidance"
            );
        }

        if (!gaps.isEmpty()) {
            appendCatalogReminder(summary, gaps.get(0));
        } else if (!strengths.isEmpty()) {
            appendCatalogReminder(summary, strengths.get(0));
        }
        return summary.toString();
    }

    private static void appendCatalogReminder(StringBuilder summary, ReflectionPracticeDTO practice) {
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

    private static boolean samePractices(List<ReflectionPracticeDTO> strengths, List<ReflectionPracticeDTO> gaps) {
        return (
            !strengths.isEmpty() &&
            strengths
                .stream()
                .map(ReflectionPracticeDTO::slug)
                .toList()
                .equals(gaps.stream().map(ReflectionPracticeDTO::slug).toList())
        );
    }

    private static String nameList(List<ReflectionPracticeDTO> cards) {
        List<String> names = cards
            .stream()
            .map(ReflectionPracticeDTO::name)
            .filter(Objects::nonNull)
            .map(name -> "“" + name + "”")
            .distinct()
            .toList();
        return switch (names.size()) {
            case 0 -> "this area";
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
