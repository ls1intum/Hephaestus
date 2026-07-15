package de.tum.cit.aet.hephaestus.integration.core.connection;

import java.io.Serial;

/** A lifecycle transition was rejected because the connection still has a sync job in flight. */
public class ConnectionBusyException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long connectionId;
    private final long jobId;

    public ConnectionBusyException(long connectionId, long jobId) {
        super("Connection " + connectionId + " has active sync job " + jobId + "; cancel or wait for it first");
        this.connectionId = connectionId;
        this.jobId = jobId;
    }

    public long connectionId() {
        return connectionId;
    }

    public long jobId() {
        return jobId;
    }
}
