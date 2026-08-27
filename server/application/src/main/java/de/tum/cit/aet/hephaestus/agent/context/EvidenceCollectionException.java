package de.tum.cit.aet.hephaestus.agent.context;

import org.jspecify.annotations.Nullable;

public final class EvidenceCollectionException extends RuntimeException {

    public EvidenceCollectionException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
