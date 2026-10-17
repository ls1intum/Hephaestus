package de.tum.cit.aet.hephaestus.practices.spi;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HostCoverage(
        boolean complete,
        @Nullable List<CoverageOmission> omitted,
        int reads,
        int truncatedReads,
        int failedReads,
        List<ReadReceipt> receipts)
        implements ResultCoverage {
    public HostCoverage {
        omitted = omitted == null ? null : List.copyOf(omitted);
        receipts = List.copyOf(receipts);
    }
}
