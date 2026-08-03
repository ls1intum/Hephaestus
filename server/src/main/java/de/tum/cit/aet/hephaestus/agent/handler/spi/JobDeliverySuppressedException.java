package de.tum.cit.aet.hephaestus.agent.handler.spi;

public final class JobDeliverySuppressedException extends JobDeliveryException {

    public JobDeliverySuppressedException(String message, Throwable cause) {
        super(message, cause);
    }
}
