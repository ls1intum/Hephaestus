package de.tum.cit.aet.hephaestus.practices.scan;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class ScanRequestRejected extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Reason {
        REQUEST_CONFLICT,
        SCAN_ACTIVE,
        REF_UNSUPPORTED,
        VERSION_CHANGED
    }

    private final Reason reason;
    private final @Nullable UUID scanId;
    private final @Nullable String value;

    public ScanRequestRejected(Reason reason, String message, @Nullable UUID scanId, @Nullable String value) {
        super(message);
        this.reason = reason;
        this.scanId = scanId;
        this.value = value;
    }

    public Reason reason() {
        return reason;
    }

    public @Nullable UUID scanId() {
        return scanId;
    }

    public @Nullable String value() {
        return value;
    }
}
