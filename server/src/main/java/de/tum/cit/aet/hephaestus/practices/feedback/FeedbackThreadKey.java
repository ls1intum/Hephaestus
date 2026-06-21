package de.tum.cit.aet.hephaestus.practices.feedback;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Stable cross-run identity of a {@link Feedback} delivery <em>unit</em> (ADR 0021, F-16) — the join key
 * that lets a re-review SUPERSEDE the prior delivery and edit its comment in place instead of posting a
 * fresh one.
 *
 * <p><strong>Identity is the destination, NOT the content.</strong> A subtle early design hashed the set
 * of finding {@code finding_fingerprint}s that composed the body — but that set churns between reviews (a
 * finding is fixed, a new one appears), which would change the unit's identity exactly when supersession
 * is most wanted. The unit is therefore keyed by <em>where it is delivered</em>: the artifact
 * {@code (artifact_type, artifact_id)}, the {@code recipient}, and the {@code surface}. Two reviews of the
 * same PR deliver the same in-context summary unit to the same author → same continuity key → the second
 * supersedes the first. The findings it references are the changing <em>content</em>, recorded separately.
 *
 * <p>Locale-safe (Locale.ROOT) lower-cased SHA-256 hex, 64 chars, matching {@code feedback.feedback_thread_key
 * VARCHAR(64)}. Pure and side-effect free.
 */
public final class FeedbackThreadKey {

    private static final char SEP = '\u001F'; // ASCII unit separator

    private FeedbackThreadKey() {}

    /**
     * Compute the stable continuity key for a delivery unit.
     *
     * @param artifactType the artifact-type discriminator (e.g. {@code PULL_REQUEST}); empty string when the
     *     unit is not artifact-anchored (a dashboard digest)
     * @param artifactId the artifact id, or {@code null} when not artifact-anchored
     * @param recipientUserId the user the unit is delivered to (required)
     * @param surface the delivery surface (required)
     * @return the lowercase SHA-256 hex digest (exactly 64 characters)
     */
    public static String compute(String artifactType, Long artifactId, long recipientUserId, FeedbackChannel surface) {
        String canonical = new StringBuilder()
            .append(artifactType == null ? "" : artifactType)
            .append(SEP)
            .append(artifactId == null ? "" : artifactId)
            .append(SEP)
            .append(recipientUserId)
            .append(SEP)
            .append(surface.name())
            .toString();
        return sha256Hex(canonical);
    }

    private static String sha256Hex(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
