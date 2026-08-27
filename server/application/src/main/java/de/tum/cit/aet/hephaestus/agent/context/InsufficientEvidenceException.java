package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.PreparedJobInputs;

public final class InsufficientEvidenceException extends JobPreparationException {

    private final PreparedJobInputs preparedInputs;

    public InsufficientEvidenceException(String message, PreparedJobInputs preparedInputs) {
        super(message);
        this.preparedInputs = preparedInputs;
    }

    public PreparedJobInputs preparedInputs() {
        return preparedInputs;
    }
}
