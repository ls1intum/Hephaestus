package de.tum.in.www1.hephaestus.agent.sandbox.spi;

/**
 * Thrown by the interactive sandbox when registration is denied because a capacity cap has been
 * hit — either {@code hephaestus.mentor.max-sessions-per-user} (a user has too many concurrent
 * sessions) or {@code hephaestus.mentor.max-sessions-total} (the replica is full).
 *
 * <p>This is a typed sibling of {@link InteractiveSandboxException} so upstream callers
 * (notably {@code MentorChatService}) can route cap rejections to a dedicated metric outcome
 * ({@code CAPACITY_EXCEEDED}) without string-matching on the message. The two cases share an
 * enum to keep the wire-format stable; the dashboard alert split between them is one tag away.
 */
public class InteractiveSandboxCapacityExceededException extends InteractiveSandboxException {

    public enum Scope {
        /** Caller has the maximum permitted concurrent sessions for their user. */
        PER_USER,
        /** This replica is at the global cap. Future capacity work may shed via a different replica. */
        GLOBAL,
    }

    private final Scope scope;

    public InteractiveSandboxCapacityExceededException(Scope scope, String message) {
        super(message);
        this.scope = scope;
    }

    public Scope scope() {
        return scope;
    }
}
