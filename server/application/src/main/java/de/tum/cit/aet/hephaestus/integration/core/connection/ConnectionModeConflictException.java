package de.tum.cit.aet.hephaestus.integration.core.connection;

/**
 * A write that the connection's current mode cannot take, such as a bearer token for a GitHub App
 * installation, which runs on its installation and stores no token. A state conflict, answered as one.
 */
public class ConnectionModeConflictException extends RuntimeException {

    public ConnectionModeConflictException(String message) {
        super(message);
    }
}
