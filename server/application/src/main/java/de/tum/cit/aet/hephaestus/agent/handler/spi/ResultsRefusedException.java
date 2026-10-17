package de.tum.cit.aet.hephaestus.agent.handler.spi;

import java.io.Serial;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Refused results stop the sandbox without retry. Stable lowercase reason codes identify the refusal in metrics and logs.
 * Messages describe individual jobs and must not serve as reason codes.
 */
public class ResultsRefusedException extends JobDeliveryException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String reasonCode;
    private final String reason;
    private final transient JsonNode verificationFailures;

    public ResultsRefusedException(String reasonCode, String reason) {
        this(reasonCode, reason, JsonNodeFactory.instance.arrayNode());
    }

    public ResultsRefusedException(String reasonCode, String reason, Throwable cause) {
        super(reason, cause);
        this.verificationFailures = JsonNodeFactory.instance.arrayNode();
        this.reasonCode = reasonCode;
        this.reason = reason;
    }

    public ResultsRefusedException(String reasonCode, String reason, JsonNode verificationFailures) {
        super(reason);
        this.verificationFailures = verificationFailures.deepCopy();
        this.reasonCode = reasonCode;
        this.reason = reason;
    }

    public JsonNode verificationFailures() {
        return verificationFailures.deepCopy();
    }

    public String reasonCode() {
        return reasonCode;
    }

    /** The refusal in words, always present — unlike {@code getMessage()}, which a caller must null-check. */
    public String reason() {
        return reason;
    }
}
