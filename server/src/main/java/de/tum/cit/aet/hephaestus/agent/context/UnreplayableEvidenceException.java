package de.tum.cit.aet.hephaestus.agent.context;

/**
 * A recorded manifest cannot be re-judged because the source contract it was captured under is no
 * longer shipped.
 *
 * <p>Distinct from invalid evidence on purpose. The recorded decision was correct when it was made
 * and the captured evidence is still exactly what was seen; only the ability to re-derive a verdict
 * from it is gone. A replay should report that honestly rather than fail as if the evidence were
 * corrupt.
 */
public class UnreplayableEvidenceException extends RuntimeException {

    public UnreplayableEvidenceException(String message) {
        super(message);
    }
}
