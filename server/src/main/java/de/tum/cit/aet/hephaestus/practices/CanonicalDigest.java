package de.tum.cit.aet.hephaestus.practices;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.jspecify.annotations.Nullable;

/**
 * SHA-256 over a sequence of fields, each framed by its byte length so that no two different field
 * sequences can produce the same input — {@code ("ab", "c")} and {@code ("a", "bc")} hash apart.
 * Every catalog digest and fingerprint is built here so they all share that guarantee.
 */
public final class CanonicalDigest {

    private final MessageDigest digest = sha256();

    public CanonicalDigest add(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
        return this;
    }

    /** A present value is distinguished from an absent one by a leading marker byte. */
    public CanonicalDigest addNullable(@Nullable String value) {
        if (value == null) {
            digest.update((byte) 0);
            return this;
        }
        digest.update((byte) 1);
        return add(value);
    }

    public CanonicalDigest addInt(int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
        return this;
    }

    public String hex() {
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
