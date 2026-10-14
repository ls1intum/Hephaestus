package de.tum.cit.aet.hephaestus.integration.docs.item;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.jspecify.annotations.Nullable;

public final class DocumentEvents {
    public static final ItemKind DOCUMENT = new ItemKind("docs.document");
    public static final ItemEventName PUBLISHED = new ItemEventName("docs.document.published");
    public static final ItemEventName UPDATED = new ItemEventName("docs.document.updated");
    public static final ItemEventName ARCHIVED = new ItemEventName("docs.document.archived");

    public static ItemVersion version(
            @Nullable String title, String contentHash, boolean archived, @Nullable String createdBySubject) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] content = (frame(title) + frame(contentHash) + archived + frame(createdBySubject))
                    .getBytes(StandardCharsets.UTF_8);
            return new ItemVersion(HexFormat.of().formatHex(digest.digest(content)), null);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }

    private static String frame(@Nullable String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    private DocumentEvents() {}
}
