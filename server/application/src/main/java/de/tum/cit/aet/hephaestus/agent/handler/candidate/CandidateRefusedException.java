package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import de.tum.cit.aet.hephaestus.integration.core.spi.CandidateRefusal;
import java.io.Serial;

/** A candidate that breaks the contract. Its message names the rule; it never quotes the candidate's text. */
public class CandidateRefusedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final CandidateRefusal refusal;

    public CandidateRefusedException(CandidateRefusal refusal, String message) {
        super(message);
        this.refusal = refusal;
    }

    public CandidateRefusedException(CandidateRefusal refusal, String message, Throwable cause) {
        super(message, cause);
        this.refusal = refusal;
    }

    public CandidateRefusal refusal() {
        return refusal;
    }

    public String reasonCode() {
        return refusal.wireValue();
    }
}
