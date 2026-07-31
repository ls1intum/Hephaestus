package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class PracticeDetectionFingerprint {

    private PracticeDetectionFingerprint() {}

    public static String of(
        String slug,
        String name,
        WorkArtifact artifactType,
        List<String> triggerEvents,
        String criteria,
        @Nullable String precomputeScript,
        @Nullable String areaSlug
    ) {
        MessageDigest digest = sha256();
        add(digest, slug);
        add(digest, name);
        add(digest, artifactType.name());
        triggerEvents
            .stream()
            .sorted()
            .forEach(event -> add(digest, event));
        add(digest, criteria);
        addNullable(digest, precomputeScript);
        addNullable(digest, areaSlug);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void addNullable(MessageDigest digest, @Nullable String value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }
        digest.update((byte) 1);
        add(digest, value);
    }

    private static void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
