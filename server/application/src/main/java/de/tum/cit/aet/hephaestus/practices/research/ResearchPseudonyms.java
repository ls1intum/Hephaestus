package de.tum.cit.aet.hephaestus.practices.research;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Keys for one export: HMAC-SHA256 under a fresh random key, separated by record kind, truncated to
 * 128 bits. The same row gets the same key everywhere in one file, so the file joins with itself.
 * The HMAC key is never stored, logged or returned. Only the participant keys are kept, in the
 * protected subject mapping, so that removal can be asked for.
 *
 * <p>This does not make two exports unlinkable. Dates, outcomes and other values still let records
 * be matched across files, so the dataset stays pseudonymous personal data.
 */
final class ResearchPseudonyms {

    enum Kind {
        PARTICIPANT("p"),
        RESULT("r"),
        PROBLEM("q"),
        REVIEW("v"),
        ITEM("i"),
        PRACTICE("s"),
        PRACTICE_VERSION("sv"),
        FEEDBACK("f"),
        JOB("j");

        private final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }
    }

    private static final char[] BASE32 = "abcdefghijklmnopqrstuvwxyz234567".toCharArray();
    private static final int KEY_BYTES = 32;
    private static final int TRUNCATED_BYTES = 16;

    private final Mac mac;

    private ResearchPseudonyms(byte[] key) {
        try {
            mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    static ResearchPseudonyms fresh(SecureRandom random) {
        byte[] key = new byte[KEY_BYTES];
        random.nextBytes(key);
        return new ResearchPseudonyms(key);
    }

    /** For tests that need two exports over the same rows under known keys. */
    static ResearchPseudonyms withKey(byte[] key) {
        return new ResearchPseudonyms(key.clone());
    }

    String key(Kind kind, Object id) {
        byte[] digest = mac.doFinal((kind.name() + '\0' + id).getBytes(StandardCharsets.UTF_8));
        return kind.prefix + "_" + base32(Arrays.copyOf(digest, TRUNCATED_BYTES));
    }

    /** 128 bits as 26 lower-case RFC 4648 base32 characters, without padding. */
    private static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder(26);
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32[(buffer >>> (bits - 5)) & 31]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32[(buffer << (5 - bits)) & 31]);
        }
        return out.toString();
    }
}
