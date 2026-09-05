package de.tum.cit.aet.hephaestus.integration.core.connection;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;

/**
 * A stored credential exists but cannot be decrypted with the keys this server runs with. That is a
 * state of the connection, not a failure of the request that met it: the operator either restores
 * the key the credential was written with or replaces the credential, and until then every request
 * that needs the plaintext gets this answer.
 */
public class CredentialUnreadableException extends RuntimeException {

    private final long connectionId;
    private final IntegrationKind kind;

    public CredentialUnreadableException(long connectionId, IntegrationKind kind) {
        super("The stored " + kind.name().toLowerCase(java.util.Locale.ROOT) + " credential of connection "
                + connectionId
                + " cannot be read with the server's current encryption key. Re-enter the credential or reconnect the"
                + " integration; if the key was changed by mistake, restore it.");
        this.connectionId = connectionId;
        this.kind = kind;
    }

    public long connectionId() {
        return connectionId;
    }

    public IntegrationKind kind() {
        return kind;
    }
}
