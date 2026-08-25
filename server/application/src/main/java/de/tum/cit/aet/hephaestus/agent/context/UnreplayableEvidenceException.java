package de.tum.cit.aet.hephaestus.agent.context;

/**
 * Review readiness cannot be re-evaluated for a recorded manifest because the source contract it was
 * captured under is no longer shipped.
 *
 * <p>Deliberately distinct from invalid evidence. The recorded result remains correct for the
 * evidence it was produced from, and that evidence is unchanged; only the ability to re-derive a
 * readiness result is unavailable. A replay reports this outcome rather than failing as though the
 * evidence were malformed.
 */
public class UnreplayableEvidenceException extends RuntimeException {

    public UnreplayableEvidenceException(String message) {
        super(message);
    }
}
