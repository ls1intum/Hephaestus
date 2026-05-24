package de.tum.cit.aet.hephaestus.integration.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * CloudEvents 1.0.2 envelope used in binary mode on NATS and structured mode on
 * outbound HTTP.
 *
 * <p>Required attributes: {@code specversion}, {@code id}, {@code source}, {@code type}.
 * Optional: {@code time}, {@code subject}, {@code datacontenttype}, {@code dataschema}.
 * Extensions used by Hephaestus: {@code partitionkey} (=scopeId), {@code traceparent},
 * {@code tracestate}, {@code vendordeliveryid}, {@code dataschemaversion},
 * {@code vendorschemaversion}, {@code correlationid}.
 *
 * <p>Per CloudEvents binding-mode reference: in binary mode, CE attributes ride
 * NATS headers ({@code ce-id}, {@code ce-source}, …); only {@code data} sits in the
 * NATS message body.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HephaestusCloudEvent(
    @NonNull String specversion,
    @NonNull String id,
    @NonNull URI source,
    @NonNull String type,
    @Nullable Instant time,
    @Nullable String subject,
    @Nullable String datacontenttype,
    @Nullable URI dataschema,
    @Nullable Integer dataschemaversion,
    @Nullable String vendorschemaversion,
    @Nullable String correlationid,
    @Nullable String vendordeliveryid,
    @Nullable String partitionkey,
    @Nullable String traceparent,
    @Nullable String tracestate,
    JsonNode data
) {

    /** CloudEvents 1.0.2. */
    public static final String SPEC_VERSION = "1.0";

    public static Builder builder() {
        return new Builder();
    }

    /** Returns a map of CE attributes suitable for emission as NATS headers (binary mode). */
    public Map<String, String> toBinaryHeaders() {
        var m = new java.util.LinkedHashMap<String, String>();
        m.put("ce-specversion", specversion);
        m.put("ce-id", id);
        m.put("ce-source", source.toString());
        m.put("ce-type", type);
        if (time != null) m.put("ce-time", time.toString());
        if (subject != null) m.put("ce-subject", subject);
        if (datacontenttype != null) m.put("content-type", datacontenttype);
        if (dataschema != null) m.put("ce-dataschema", dataschema.toString());
        if (dataschemaversion != null) m.put("ce-dataschemaversion", dataschemaversion.toString());
        if (vendorschemaversion != null) m.put("ce-vendorschemaversion", vendorschemaversion);
        if (correlationid != null) m.put("ce-correlationid", correlationid);
        if (vendordeliveryid != null) m.put("ce-vendordeliveryid", vendordeliveryid);
        if (partitionkey != null) m.put("ce-partitionkey", partitionkey);
        if (traceparent != null) m.put("traceparent", traceparent);
        if (tracestate != null) m.put("tracestate", tracestate);
        return m;
    }

    public static final class Builder {
        private String id;
        private URI source;
        private String type;
        private Instant time;
        private String subject;
        private String datacontenttype = "application/json";
        private URI dataschema;
        private Integer dataschemaversion;
        private String vendorschemaversion;
        private String correlationid;
        private String vendordeliveryid;
        private String partitionkey;
        private String traceparent;
        private String tracestate;
        private JsonNode data;

        public Builder id(String v) { this.id = v; return this; }
        public Builder source(URI v) { this.source = v; return this; }
        public Builder type(String v) { this.type = v; return this; }
        public Builder time(Instant v) { this.time = v; return this; }
        public Builder subject(String v) { this.subject = v; return this; }
        public Builder datacontenttype(String v) { this.datacontenttype = v; return this; }
        public Builder dataschema(URI v) { this.dataschema = v; return this; }
        public Builder dataschemaversion(Integer v) { this.dataschemaversion = v; return this; }
        public Builder vendorschemaversion(String v) { this.vendorschemaversion = v; return this; }
        public Builder correlationid(String v) { this.correlationid = v; return this; }
        public Builder vendordeliveryid(String v) { this.vendordeliveryid = v; return this; }
        public Builder partitionkey(String v) { this.partitionkey = v; return this; }
        public Builder traceparent(String v) { this.traceparent = v; return this; }
        public Builder tracestate(String v) { this.tracestate = v; return this; }
        public Builder data(JsonNode v) { this.data = v; return this; }

        public HephaestusCloudEvent build() {
            return new HephaestusCloudEvent(
                SPEC_VERSION, id, source, type, time, subject, datacontenttype, dataschema,
                dataschemaversion, vendorschemaversion, correlationid, vendordeliveryid,
                partitionkey, traceparent, tracestate, data
            );
        }
    }
}
