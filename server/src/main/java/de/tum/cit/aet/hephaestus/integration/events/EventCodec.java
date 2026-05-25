package de.tum.cit.aet.hephaestus.integration.events;

import tools.jackson.databind.JsonNode;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;

/**
 * Per-vendor translator between the wire-format {@link HephaestusCloudEvent} and an
 * in-process domain event type owned by the vendor's family-lib.
 *
 * <p>Codecs are registered by {@code ce.type} prefix; the registry does
 * longest-prefix matching ({@code "com.hephaestus.github.pullrequest.created"} →
 * {@code "com.hephaestus.github."}).
 */
public interface EventCodec {

    IntegrationKind kind();

    /**
     * Type prefix the codec answers for (e.g. {@code "com.hephaestus.github."}).
     * Longest-prefix wins at dispatch time.
     */
    String typePrefix();

    /** Wire → in-process. Implementations cast to their own sealed domain type. */
    Object decode(HephaestusCloudEvent event);

    /** In-process → wire. */
    HephaestusCloudEvent encode(Object domainEvent, EncodeHints hints);

    /**
     * Hints supplied by the publisher (scope id for partitioning, correlation
     * propagation, idempotency overrides). Most codecs read partitionkey + correlationid.
     */
    record EncodeHints(
        String partitionkey,
        String correlationid,
        String vendordeliveryid,
        Integer dataschemaversion,
        String vendorschemaversion,
        JsonNode rawSource
    ) {
    }
}
