package de.tum.cit.aet.hephaestus.integration.core.signal;

/**
 * How a signal's {@link SignalRevision} is derived — declared <em>per signal</em>, never per artifact kind,
 * because a description edit moves no commit SHA and a push changes no prose. Recoverable from a stored
 * revision, so a ledger row explains its own identity without consulting the catalog that produced it.
 */
public enum RevisionScheme {
    /** The artifact's head commit. For signals whose subject is the code. */
    HEAD_COMMIT("sha~"),

    /** A digest of the authored prose. For signals whose subject is what a human wrote. */
    CONTENT_DIGEST("dig~"),

    /** The state that ended the artifact's life. Fires at most once, so a redelivery is inert. */
    TERMINAL_STATE("state~"),

    /** The identity of one explicit request to review. Two asks are two revisions, by construction. */
    RUN_ID("run~");

    private final String prefix;

    RevisionScheme(String prefix) {
        this.prefix = prefix;
    }

    /**
     * The marker a revision starts with; {@code '~'} because revisions travel inside colon-delimited
     * idempotency keys.
     */
    public String prefix() {
        return prefix;
    }
}
