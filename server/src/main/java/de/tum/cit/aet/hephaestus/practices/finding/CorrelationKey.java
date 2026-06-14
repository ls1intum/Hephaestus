package de.tum.cit.aet.hephaestus.practices.finding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Deterministic cross-run identity for a {@code PracticeFinding} (ADR 0021, C2).
 *
 * <p>The correlation key answers "is this the <em>same</em> finding we surfaced on an earlier agent
 * run?" so feedback can supersede rather than re-post, and a contributor's reaction history can follow
 * one underlying problem across re-detections. It is therefore a stable hash of <em>what the finding is
 * about</em>, never of <em>when</em> it was produced:
 *
 * <ul>
 *   <li>{@code practiceSlug} — the practice's stable per-workspace slug (NOT its surrogate id, which is
 *       workspace-local and survives reseeds poorly); identity is per-practice.</li>
 *   <li>{@code targetType} + {@code targetId} — the artifact under review (PR / ISSUE).</li>
 *   <li>{@code subjectUserId} falling back to {@code contributorId} — the person the finding is
 *       <em>about</em>. For author-side practices this is the contributor; for reviewer-side practices
 *       the subject differs, and two reviewers on one PR must not collapse to one key.</li>
 *   <li>a <em>content anchor</em> = the finding {@code title} plus the file {@code path} of its first
 *       evidence location. The path locates the problem stably; the title disambiguates two findings of
 *       the same practice on the same file.</li>
 * </ul>
 *
 * <p><strong>Deliberately excluded</strong> from the digest, because they are not stable across runs:
 * the agent job id (a new id every run), and any line number / column / range (edits shift lines; the
 * same logical problem must keep its key when surrounding code moves). Only the evidence <em>path</em>
 * participates — callers MUST pass the path of the first location and MUST NOT fold in a line.
 *
 * <p>Output is the lowercase SHA-256 hex digest: 64 chars, matching {@code practice_finding
 * .correlation_key VARCHAR(64)}. Pure and side-effect free; safe to call before persistence.
 */
public final class CorrelationKey {

    /** Field separator chosen to never appear inside a slug, enum name, numeric id, or path segment. */
    private static final char SEP = '\u001F'; // ASCII unit separator

    private CorrelationKey() {}

    /**
     * Compute the stable 64-char correlation key for a finding.
     *
     * @param practiceSlug the practice's stable slug (required)
     * @param targetType the artifact-type discriminator, e.g. {@code PULL_REQUEST} / {@code ISSUE} (required)
     * @param targetId the artifact id under review (required)
     * @param aboutUserId the user the finding is ABOUT — the subject for reviewer-side practices, else the
     *     contributor; the caller resolves the {@code subjectUserId ?? contributorId} coalesce so the same
     *     underlying problem keeps one identity regardless of which of the two ids carries it
     * @param title the finding title — half of the content anchor (required)
     * @param firstLocationPath the file path of the finding's first evidence location, or {@code null}
     *     when the practice has no file location (e.g. PR-description quality). PASS THE PATH ONLY —
     *     never a line number.
     * @return the lowercase SHA-256 hex digest (exactly 64 characters)
     */
    public static String compute(
        String practiceSlug,
        String targetType,
        long targetId,
        long aboutUserId,
        String title,
        @Nullable String firstLocationPath
    ) {
        Objects.requireNonNull(practiceSlug, "practiceSlug");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(title, "title");

        // Title is normalised (locale-fixed lower-case + collapsed whitespace) so trivial rewording or
        // re-spacing of the same finding keeps one identity; slug/type/path stay verbatim (they are
        // machine values, not prose).
        String canonical = new StringBuilder()
            .append(practiceSlug)
            .append(SEP)
            .append(targetType)
            .append(SEP)
            .append(targetId)
            .append(SEP)
            .append(aboutUserId)
            .append(SEP)
            .append(normalizeAnchorText(title))
            .append(SEP)
            .append(firstLocationPath == null ? "" : firstLocationPath)
            .toString();

        return sha256Hex(canonical);
    }

    /** Locale-fixed (Locale.ROOT — LocaleSafetyArchTest) lower-case + whitespace collapse for the anchor title. */
    private static String normalizeAnchorText(String text) {
        return text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String sha256Hex(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by every JVM; absence is unrecoverable.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
