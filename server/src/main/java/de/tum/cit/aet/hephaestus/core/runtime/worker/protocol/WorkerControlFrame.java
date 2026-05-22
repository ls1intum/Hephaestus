package de.tum.cit.aet.hephaestus.core.runtime.worker.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Sealed type of worker control-channel frames. Jackson keys on the {@code "type"} property. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = WorkerHello.class, name = "WorkerHello"),
        @JsonSubTypes.Type(value = WorkerWelcome.class, name = "WorkerWelcome"),
        @JsonSubTypes.Type(value = Heartbeat.class, name = "Heartbeat"),
        @JsonSubTypes.Type(value = CapacityReport.class, name = "CapacityReport"),
        @JsonSubTypes.Type(value = ForceReconnect.class, name = "ForceReconnect"),
        @JsonSubTypes.Type(value = SessionOpen.class, name = "SessionOpen"),
        @JsonSubTypes.Type(value = SessionInput.class, name = "SessionInput"),
        @JsonSubTypes.Type(value = SessionOutput.class, name = "SessionOutput"),
        @JsonSubTypes.Type(value = SessionClose.class, name = "SessionClose"),
    }
)
public sealed interface WorkerControlFrame
    permits
        WorkerHello,
        WorkerWelcome,
        Heartbeat,
        CapacityReport,
        ForceReconnect,
        SessionOpen,
        SessionInput,
        SessionOutput,
        SessionClose {}
