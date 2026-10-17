package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;
import java.util.UUID;

public record CopyWriteResult(List<CopyReceipt> receipts, List<UUID> withheldCopyIds) {
    public CopyWriteResult {
        receipts = List.copyOf(receipts);
        withheldCopyIds = List.copyOf(withheldCopyIds);
    }

    public static CopyWriteResult of(List<CopyReceipt> receipts) {
        return new CopyWriteResult(receipts, List.of());
    }

    public static CopyWriteResult withheld(List<CopyReceipt> receipts, List<UUID> withheldCopyIds) {
        return new CopyWriteResult(receipts, withheldCopyIds);
    }
}
