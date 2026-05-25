package de.tum.cit.aet.hephaestus.integration.registry;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.springframework.lang.Nullable;

/**
 * AES-GCM Additional Authenticated Data context bound to a single {@link Connection}
 * row. Closes the cross-row substitution attack the static-AAD v1 format left open.
 *
 * <p>Construction is private to the {@code registry} package — only {@link Connection}
 * itself ({@code setCredentials} / {@code credentials}) and the {@link CredentialBundleConverter}
 * test surface should build one. Caller-provided context would defeat the purpose
 * (caller A could pass B's context).
 *
 * <p>Layout (per AWS Encryption SDK + Vault Transit conventions: length-prefix every
 * variable field, no delimiters):
 * <pre>
 *   "hephaestus-credential-bundle" || 0x1F      // 28-byte domain separator + US
 *   u8(2)                                       // AAD schema version
 *   u16_be(len(workspaceId_ascii)) || ascii     // Long → decimal string
 *   u16_be(len(kind))               || utf8     // e.g. "GITHUB"
 *   u16_be(len(instanceKey))        || utf8     // "" when null (pre-bind OAuth slot)
 *   u16_be(len(columnFqn))          || utf8     // e.g. "connection.credentials"
 * </pre>
 *
 * @param workspaceId Hephaestus workspace primary key
 * @param kind        integration kind for this Connection
 * @param instanceKey vendor-supplied identifier ({@code null} until OAuth finalize)
 * @param columnFqn   stable column FQN — future-proof against a second encrypted column
 */
public record EncryptionContext(
    long workspaceId,
    IntegrationKind kind,
    @Nullable String instanceKey,
    String columnFqn
) {

    /** AAD schema version. Bump when fields change shape; tolerant decrypt switches on this byte. */
    public static final byte SCHEMA_VERSION_V2 = 0x02;

    private static final byte[] DOMAIN_SEPARATOR =
        "hephaestus-credential-bundle".getBytes(StandardCharsets.UTF_8);

    public EncryptionContext {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (columnFqn == null || columnFqn.isBlank()) {
            throw new IllegalArgumentException("columnFqn must not be blank");
        }
    }

    /**
     * Canonical context for {@code Connection.credentials_encrypted}. Only call site
     * outside this package is the JPA entity {@link Connection}; arch-test pins this.
     */
    static EncryptionContext forConnectionCredentials(long workspaceId, IntegrationKind kind, @Nullable String instanceKey) {
        return new EncryptionContext(workspaceId, kind, instanceKey, "connection.credentials_encrypted");
    }

    /** Serialise to the AAD byte sequence — see class javadoc for layout. */
    public byte[] toAad() {
        byte[] workspaceBytes = Long.toString(workspaceId).getBytes(StandardCharsets.US_ASCII);
        byte[] kindBytes = kind.name().getBytes(StandardCharsets.UTF_8);
        byte[] instanceBytes = (instanceKey == null ? "" : instanceKey).getBytes(StandardCharsets.UTF_8);
        byte[] columnBytes = columnFqn.getBytes(StandardCharsets.UTF_8);

        int len = DOMAIN_SEPARATOR.length
            + 1                                          // schema version
            + 2 + workspaceBytes.length
            + 2 + kindBytes.length
            + 2 + instanceBytes.length
            + 2 + columnBytes.length;

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
