package de.tum.cit.aet.hephaestus.practices;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.jspecify.annotations.Nullable;

final class PracticeDefinitionDigest {

    private PracticeDefinitionDigest() {}

    static String digest(String slug, PracticeDefinition definition) {
        MessageDigest digest = sha256();
        add(digest, slug);
        add(digest, definition.name());
        add(digest, definition.artifactType().name());
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(definition.triggerEvents().size()).array());
        definition.triggerEvents().forEach(trigger -> add(digest, trigger));
        add(digest, definition.criteria());
        addNullable(digest, definition.precomputeScript());
        addNullable(digest, definition.whyItMatters());
        addNullable(digest, definition.whatGoodLooksLike());
        addNullable(digest, definition.areaSlug());
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
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
