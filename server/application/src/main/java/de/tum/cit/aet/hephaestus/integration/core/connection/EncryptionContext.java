package de.tum.cit.aet.hephaestus.integration.core.connection;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/**
 * AES-GCM Additional Authenticated Data bound to a connection row.
 *
 * <p>Persisted layout:
 * <pre>
 *   "hephaestus-credential-bundle\x1f"
 *   u8(2)
 *   u16_be(len(workspaceId_ascii)) || ascii
 *   u16_be(len(kind))               || utf8
 *   u16_be(len(instanceKey))        || utf8
 *   u16_be(len(columnFqn))          || utf8
 * </pre>
 *
 * @param workspaceId Hephaestus workspace primary key
 * @param kind        integration kind for this Connection
 * @param instanceKey vendor-supplied identifier ({@code null} until OAuth finalize)
 * @param columnFqn   stable column FQN — future-proof against a second encrypted column
 */
public record EncryptionContext(
        long workspaceId, IntegrationKind kind, @Nullable String instanceKey, String columnFqn) {
    public static final byte SCHEMA_VERSION_V2 = 0x02;

    private static final byte[] DOMAIN_SEPARATOR = "hephaestus-credential-bundle".getBytes(StandardCharsets.UTF_8);

    public EncryptionContext {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (instanceKey != null && instanceKey.isBlank()) {
            throw new IllegalArgumentException("instanceKey must not be blank");
        }
        if (columnFqn == null || columnFqn.isBlank()) {
            throw new IllegalArgumentException("columnFqn must not be blank");
        }
    }

    static EncryptionContext forConnectionCredentials(
            long workspaceId, IntegrationKind kind, @Nullable String instanceKey) {
        return new EncryptionContext(workspaceId, kind, instanceKey, "connection.credentials_encrypted");
    }

    public byte[] toAad() {
        byte[] workspaceBytes = Long.toString(workspaceId).getBytes(StandardCharsets.US_ASCII);
        byte[] kindBytes = kind.name().getBytes(StandardCharsets.UTF_8);
        byte[] instanceBytes = (instanceKey == null ? "" : instanceKey).getBytes(StandardCharsets.UTF_8);
        byte[] columnBytes = columnFqn.getBytes(StandardCharsets.UTF_8);

        int len = DOMAIN_SEPARATOR.length
                + 1
                + // schema version
                2
                + workspaceBytes.length
                + 2
                + kindBytes.length
                + 2
                + instanceBytes.length
                + 2
                + columnBytes.length;

        ByteBuffer buf = ByteBuffer.allocate(len);
        buf.put(DOMAIN_SEPARATOR);
        buf.put(SCHEMA_VERSION_V2);
        writeLengthPrefixed(buf, workspaceBytes);
        writeLengthPrefixed(buf, kindBytes);
        writeLengthPrefixed(buf, instanceBytes);
        writeLengthPrefixed(buf, columnBytes);
        return buf.array();
    }

    private static void writeLengthPrefixed(ByteBuffer buf, byte[] bytes) {
        if (bytes.length > 0xFFFF) {
            throw new IllegalArgumentException("AAD field exceeds u16 length limit: " + bytes.length);
        }
        buf.putShort((short) bytes.length);
        buf.put(bytes);
    }
}
