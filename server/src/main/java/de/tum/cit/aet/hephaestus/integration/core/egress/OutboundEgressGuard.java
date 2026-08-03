package de.tum.cit.aet.hephaestus.integration.core.egress;

import de.tum.cit.aet.hephaestus.core.settings.spi.SilentModeQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Final, fail-closed check immediately before an external delivery-write attempt. */
@Component
public class OutboundEgressGuard {

    private static final Logger log = LoggerFactory.getLogger(OutboundEgressGuard.class);

    private final SilentModeQuery silentModeQuery;

    public OutboundEgressGuard(SilentModeQuery silentModeQuery) {
        this.silentModeQuery = silentModeQuery;
    }

    public void requireDeliveryAllowed(String operation) {
        Decision decision = decide(operation);
        if (!decision.allowed()) {
            if (decision.cause() != null) {
                throw new OutboundEgressSuppressedException(operation, decision.cause());
            }
            throw new OutboundEgressSuppressedException(operation);
        }
    }

    public boolean deliveryAllowed(String operation) {
        return decide(operation).allowed();
    }

    private Decision decide(String operation) {
        final boolean engaged;
        try {
            engaged = silentModeQuery.isSilentModeEngaged();
        } catch (RuntimeException e) {
            log.error("Outbound operation blocked: Silent Mode state unavailable, operation={}", operation, e);
            return new Decision(false, e);
        }
        if (engaged) {
            log.debug("Outbound operation suppressed: operation={}", operation);
            return new Decision(false, null);
        }
        return new Decision(true, null);
    }

    private record Decision(boolean allowed, RuntimeException cause) {}
}
