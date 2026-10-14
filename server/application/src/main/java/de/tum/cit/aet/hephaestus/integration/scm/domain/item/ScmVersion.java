package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.jspecify.annotations.Nullable;

public final class ScmVersion {
    private ScmVersion() {}

    public static ItemVersion pullRequest(String base, String head, String title, @Nullable String body) {
        requireObjectId(base);
        requireObjectId(head);
        return new ItemVersion("scm.pull_request:v1:" + base + ":" + head + ":" + digest(title, body), base);
    }

    public static ItemVersion pullRequestReview(
            String base, String head, String title, @Nullable String body, ReviewContent review) {
        requireObjectId(base);
        requireObjectId(head);
        requireObjectId(review.commit());
        return new ItemVersion(
                "scm.pull_request_review:v1:"
                        + base
                        + ":"
                        + head
                        + ":"
                        + review.commit()
                        + ":"
                        + digest(title, body, review.body(), review.state()),
                base);
    }

    public record ReviewContent(String commit, @Nullable String body, String state) {}

    public static ItemVersion withContext(ItemVersion version, ItemVersion context) {
        reviewedHead(version);
        int last = version.value().lastIndexOf(':');
        return new ItemVersion(
                version.value().substring(0, last + 1) + digest(version.value(), context.value()),
                version.baseVersion());
    }

    public static String reviewedHead(ItemVersion version) {
        String[] fields = version.value().split(":", -1);
        if (fields.length < 2
                || !fields[1].equals("v1")
                || !((fields[0].equals(ScmItemEvents.PULL_REQUEST.value()) && fields.length == 5)
                        || (fields[0].equals(ScmItemEvents.PULL_REQUEST_REVIEW.value()) && fields.length == 6)))
            throw new IllegalArgumentException("Not an SCM review version");
        requireObjectId(fields[2]);
        requireObjectId(fields[3]);
        if (fields.length == 6) requireObjectId(fields[4]);
        if (!fields[fields.length - 1].matches("[0-9a-f]{64}") || !fields[2].equals(version.baseVersion()))
            throw new IllegalArgumentException("Invalid SCM review version");
        return fields[3];
    }

    public static ItemVersion ofContentDigest(@Nullable String... content) {
        return new ItemVersion("scm.content:v1:" + digest(content), null);
    }

    private static String digest(@Nullable String... content) {
        MessageDigest hash;
        try {
            hash = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        for (String part : content) {
            hash.update((byte) (part == null ? 0 : 1));
            if (part != null) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                hash.update(
                        ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                hash.update(bytes);
            }
        }
        return HexFormat.of().formatHex(hash.digest());
    }

    private static void requireObjectId(String value) {
        if (!value.matches("[0-9a-f]{40}|[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid Git object id");
    }

    public static String contentHash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
