package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.practices.observation.TrendDelta;
import de.tum.cit.aet.hephaestus.practices.observation.TrendDelta.LocusTransition;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Renders the cross-run progress-delta footer (ADR 0021, B1/B3) — the ONLY surface that makes behavior
 * change visible to its subject. Appended, collapsed, at the
 * bottom of the persistent summary so it never competes with the lead blocking-issue count.
 *
 * <p>Renders nothing unless the run actually moved (something resolved, appeared, or slipped back): a
 * re-review that re-flags the exact same loci is not progress and stays silent. Positive reinforcement of
 * the act of fixing (the "Resolved" wins) leads; backslides ("Slipped back") follow as the actionable part.
 */
final class ProgressFooterRenderer {

    private ProgressFooterRenderer() {}

    /** The footer markdown, or "" when there is no meaningful change to show (or no prior run to diff). */
    static String render(@Nullable TrendDelta delta) {
        if (delta == null || !delta.hasMeaningfulChange()) {
            return "";
        }
        var sb = new StringBuilder(512);
        sb.append("---\n");
        sb.append("<details><summary>📈 Progress since your last review</summary>\n\n");

        List<String> parts = new ArrayList<>();
        if (delta.countResolved() > 0) {
            parts.add("**" + delta.countResolved() + " resolved**");
        }
        if (delta.countNew() > 0) {
            parts.add("**" + delta.countNew() + " new**");
        }
        if (delta.countRegressed() > 0) {
            parts.add("**" + delta.countRegressed() + " slipped back**");
        }
        if (delta.countPersisted() > 0) {
            parts.add(delta.countPersisted() + " still open");
        }
        sb.append("Since your last review: ").append(String.join(", ", parts)).append(".\n\n");

        List<LocusTransition> resolved = delta.resolved();
        if (!resolved.isEmpty()) {
            sb.append("**Resolved ✓**\n");
            for (LocusTransition t : resolved) {
                sb.append("- ").append(titleOf(t)).append(" (`").append(t.practiceSlug()).append("`)\n");
            }
            sb.append("\n");
        }

        List<LocusTransition> regressed = delta.regressed();
        if (!regressed.isEmpty()) {
            sb.append("**Slipped back** — these were satisfied last time and are flagged again:\n");
            for (LocusTransition t : regressed) {
                sb.append("- ").append(titleOf(t)).append(" (`").append(t.practiceSlug()).append("`)\n");
            }
            sb.append("\n");
        }

        sb.append("</details>");
        return sb.toString();
    }

    private static String titleOf(LocusTransition t) {
        String title = t.title();
        if (title == null || title.isBlank()) {
            return t.practiceSlug();
        }
        // The title is LLM-authored (untrusted) and is interpolated into the footer AFTER the summary body
        // has already been sanitized, so it would otherwise bypass the bidi / zero-width / HTML-comment
        // scrub. Sanitize it here and collapse internal newlines so the markdown list item stays one line.
        String clean = PullRequestCommentPoster.sanitize(title).replaceAll("[\\r\\n]+", " ").strip();
        return clean.isBlank() ? t.practiceSlug() : clean;
    }
}
