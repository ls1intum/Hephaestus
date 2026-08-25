package de.tum.cit.aet.hephaestus.practices.feedback.approval;

import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class FeedbackApprovalDigest {

    private FeedbackApprovalDigest() {}

    public static String of(Feedback feedback) {
        String canonical = String.join(
            "\n",
            feedback.getChannel().name(),
            String.valueOf(feedback.getArtifactKind()),
            String.valueOf(feedback.getArtifactId()),
            String.valueOf(feedback.getRecipientUserId()),
            String.valueOf(feedback.getAboutUserId()),
            feedback.getBody() == null ? "" : feedback.getBody()
        );
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }
}
