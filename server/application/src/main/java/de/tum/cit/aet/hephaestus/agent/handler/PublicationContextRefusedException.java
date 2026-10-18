package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import java.io.Serial;

/** A public piece rests on history that changed, or on a write of another review whose outcome is unknown. */
public class PublicationContextRefusedException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final FeedbackStopReason reason;

    public PublicationContextRefusedException(FeedbackStopReason reason) {
        super("The public history this piece was composed from is no longer current");
        this.reason = reason;
    }

    public FeedbackStopReason reason() {
        return reason;
    }
}
