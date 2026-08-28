package de.tum.cit.aet.hephaestus.integration.core.connection;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

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
 *   "hephaestus-credential-bundle"              // 28-byte domain separator
 *   u8(2)                                       // AAD schema version
 *   u16_be(len(workspaceId_ascii)) || ascii     // Long → decimal string
 *   u16_be(len(kind))               || utf8     // e.g. "GITHUB"
 *   u16_be(len(instanceKey))        || utf8     // "" when null (pre-bind OAuth slot)
 *   u16_be(len(columnFqn))          || utf8     // e.g. "connection.credentials_encrypted"
 * </pre>
 *
 * @param workspaceId Hephaestus workspace primary key
 * @param kind        integration kind for this Connection
 * @param instanceKey vendor-supplied identifier ({@code null} until OAuth finalize)
 * @param columnFqn   stable column FQN — future-proof against a second encrypted column
 */
public record EncryptionContext(
        long workspaceId, IntegrationKind kind, @Nullable String instanceKey, String columnFqn) {
    /** AAD schema version. Only V2 is supported; decrypt rejects any other version byte. */
    public static final byte SCHEMA_VERSION_V2 = 0x02;

    private static final byte[] DOMAIN_SEPARATOR = "hephaestus-credential-bundle".getBytes(StandardCharsets.UTF_8);

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
    static EncryptionContext forConnectionCredentials(
            long workspaceId, IntegrationKind kind, @Nullable String instanceKey) {
        return new EncryptionContext(workspaceId, kind, instanceKey, "connection.credentials_encrypted");
    }

    /** Serialise to the AAD byte sequence — see class javadoc for layout. */
    public byte[] toAad() {
        byte[] workspaceBytes = Long.toString(workspaceId).getBytes(StandardCharsets.US_ASCII);
        byte[] kindBytes = kind.name().getBytes(StandardCharsets.UTF_8);
        byte[] instanceBytes = (instanceKey == null ? "" : instanceKey).getBytes(StandardCharsets.UTF_8);
        byte[] columnBytes = columnFqn.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(DOMAIN_SEPARATOR);
        out.write(SCHEMA_VERSION_V2);
        writeLengthPrefixed(out, workspaceBytes);
        writeLengthPrefixed(out, kindBytes);
        writeLengthPrefixed(out, instanceBytes);
        writeLengthPrefixed(out, columnBytes);
        return out.toByteArray();
    }

    private static void writeLengthPrefixed(ByteArrayOutputStream out, byte[] bytes) {
        if (bytes.length > 0xFFFF) {
            throw new IllegalArgumentException("AAD field exceeds u16 length limit: " + bytes.length);
        }
        out.write(bytes.length >>> 8);
        out.write(bytes.length);
        out.writeBytes(bytes);
    }
}
