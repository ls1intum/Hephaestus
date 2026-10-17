package de.tum.cit.aet.hephaestus.practices.review.report;

import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReasonCountDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ProblemChangesDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.RemovalGuardDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ScanCountsDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ScanPracticeReportDTO;
import de.tum.cit.aet.hephaestus.practices.spi.Comparison;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record FrozenScanReport(
        int schemaVersion,
        boolean listingComplete,
        @Nullable UUID baselineScanId,
        Comparison comparison,
        ScanCountsDTO recordedCounts,
        ProblemChangesDTO problemChanges,
        List<ScanPracticeReportDTO> practices,
        List<ReasonCountDTO> withheldReasons,
        List<ReasonCountDTO> notReviewedReasons,
        RemovalGuardDTO removalGuard,
        boolean expired,
        int expiredProofPairs,
        int redactedProofPairs) {
    public FrozenScanReport {
        practices = List.copyOf(practices);
        withheldReasons = List.copyOf(withheldReasons);
        notReviewedReasons = List.copyOf(notReviewedReasons);
    }
}
