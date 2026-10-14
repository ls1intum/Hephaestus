package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record CopyUpdateReceipt(
        UUID copyId,
        CopyUpdateKind kind,
        @Nullable String externalRef,
        @Nullable String externalUrl,
        @Nullable String reason) {
    public static CopyUpdateReceipt edited(UUID copyId, SummaryHandle handle) {
        return new CopyUpdateReceipt(copyId, CopyUpdateKind.EDITED, handle.externalRef(), handle.url(), null);
    }

    public static CopyUpdateReceipt gone(UUID copyId, String reason) {
        return new CopyUpdateReceipt(copyId, CopyUpdateKind.GONE, null, null, reason);
    }

    public static CopyUpdateReceipt transientFailure(UUID copyId, String reason) {
        return new CopyUpdateReceipt(copyId, CopyUpdateKind.TRANSIENT, null, null, reason);
    }
}
