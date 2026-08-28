package de.tum.cit.aet.hephaestus.agent.mentor.chat.exception;

import java.io.Serial;
import org.springframework.modulith.NamedInterface;

/**
 * Raised when a channel send fails because the peer is gone (SSE socket closed, or a Slack stream write is
 * rejected because the user deleted the message). Control-flow only. Part of the {@code mentor-chat} named
 * interface so non-{@code agent} channel adapters (e.g. the Slack streaming adapter) can signal a disconnect.
 */
@NamedInterface(name = "mentor-chat")
public final class ClientDisconnectedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Used when the disconnect was observed without a wrapping I/O failure (e.g. the lifecycle
     * flag flipped between sandbox attach and the next send attempt).
     */
    public ClientDisconnectedException(String message) {
        super(message);
    }

    public ClientDisconnectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
