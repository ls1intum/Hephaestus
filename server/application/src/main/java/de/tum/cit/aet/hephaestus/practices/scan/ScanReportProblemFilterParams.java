package de.tum.cit.aet.hephaestus.practices.scan;

import de.tum.cit.aet.hephaestus.practices.review.report.ScanMembershipQueries;
import de.tum.cit.aet.hephaestus.practices.spi.ProblemBaselineState;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Getter
@Setter
public class ScanReportProblemFilterParams {
    private @Nullable String cursor;

    @Min(1)
    @Max(200)
    @Schema(defaultValue = "50")
    private int size = 50;

    private @Nullable String practiceSlug;
    private @Nullable Severity severity;
    private @Nullable String path;
    private @Nullable ProblemBaselineState baselineState;

    @Schema(defaultValue = "true")
    private boolean openOnly = true;

    ScanMembershipQueries.ProblemFilter filter() {
        return new ScanMembershipQueries.ProblemFilter(practiceSlug, severity, path, baselineState, openOnly);
    }
}
