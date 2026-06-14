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
 *   <li>a <em>locus anchor</em> = the file {@code path} of the finding's first evidence location (empty
 *       when the practice has no file location). The path stably locates the concern within the artifact.</li>
 * </ul>
 *
 * <p><strong>Deliberately excluded</strong> from the digest, because they are not stable across runs:
 * the agent job id (a new id every run); any line number / column / range (edits shift lines); and —
 * critically — the finding <em>title</em>. A live two-run E2E proved the title makes identity inert: the
 * LLM re-words the same underlying concern every run ("DoD ticks 'All tests pass' with zero tests" vs
 * "'All tests pass' ticked but no tests exist"), so a title-anchored key never correlated across runs
 * (0/26 shared). Identity is therefore at the <em>(practice, artifact, subject, file)</em> locus grain —
 * the right grain for the research question "did the practice-concern at this locus persist or resolve?",
 * not "did this exact prose recur". Two distinct findings of one practice in one file collapse to one
 * locus; that is intentional (they are the same practice concern there). Only the evidence <em>path</em>
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
     * @param firstLocationPath the file path of the finding's first evidence location, or {@code null}
     *     when the practice has no file location (e.g. PR-description quality). PASS THE PATH ONLY —
     *     never a line number. Normalised (locale-fixed lower-case + trim) so trivial path casing/spacing
     *     does not split identity.
     * @return the lowercase SHA-256 hex digest (exactly 64 characters)
     */
    public static String compute(
        String practiceSlug,
        String targetType,
        long targetId,
        long aboutUserId,
        @Nullable String firstLocationPath
    ) {
        Objects.requireNonNull(practiceSlug, "practiceSlug");
        Objects.requireNonNull(targetType, "targetType");

        String canonical = new StringBuilder()
            .append(practiceSlug)
            .append(SEP)
            .append(targetType)
            .append(SEP)
            .append(targetId)
            .append(SEP)
            .append(aboutUserId)
            .append(SEP)
            .append(firstLocationPath == null ? "" : normalizeAnchorText(firstLocationPath))
            .toString();

        return sha256Hex(canonical);
    }

    /** Locale-fixed (Locale.ROOT — LocaleSafetyArchTest) lower-case + whitespace collapse for the anchor. */
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
