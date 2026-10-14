package de.tum.cit.aet.hephaestus.practices.scan;

import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Getter
@Setter
public class ScanReportItemFilterParams {
    private @Nullable String cursor;

    @Min(1)
    @Max(200)
    @Schema(defaultValue = "50")
    private int size = 50;

    private @Nullable String practiceSlug;
    private @Nullable ResultOutcome outcome;
    private @Nullable String status;
    private @Nullable Boolean reused;
    private @Nullable String path;

    ScanQueryService.ItemFilter filter() {
        return new ScanQueryService.ItemFilter(practiceSlug, outcome, status, reused, path);
    }
}
