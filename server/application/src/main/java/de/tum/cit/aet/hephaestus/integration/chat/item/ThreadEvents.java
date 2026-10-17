package de.tum.cit.aet.hephaestus.integration.chat.item;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ThreadEvents {
    public static final ItemKind THREAD = new ItemKind("chat.thread");
    public static final ItemEventName SETTLED = new ItemEventName("chat.thread.settled");

    public static ItemVersion version(byte[] messages, String lastTs) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(messages);
            digest.update(lastTs.getBytes(StandardCharsets.UTF_8));
            return new ItemVersion(HexFormat.of().formatHex(digest.digest()), null);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }

    private ThreadEvents() {}
}
