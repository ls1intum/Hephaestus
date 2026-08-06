package de.tum.cit.aet.hephaestus.integration.core.signal;

/**
 * How a signal's {@link SignalRevision} is derived — declared <em>per signal</em>, never per artifact
 * kind.
 *
 * <p>This is the distinction the whole ledger turns on. Editing a merge request's description moves
 * no commit SHA, so if every pull-request signal keyed on the head SHA then a practice whose criteria
 * are entirely about the description could never be re-measured after the author fixed it — the
 * coaching loop the practice exists for would run exactly once. Conversely, keying a push on the
 * description would re-review unchanged code.
 *
 * <p>The scheme is recoverable from a stored revision (each carries its scheme's prefix), so a
 * ledger row explains its own identity without consulting the catalog that produced it.
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
     * The marker a revision of this scheme starts with. {@code '~'} rather than {@code ':'} because
     * revisions travel inside colon-delimited idempotency keys.
     */
    public String prefix() {
        return prefix;
    }
}
