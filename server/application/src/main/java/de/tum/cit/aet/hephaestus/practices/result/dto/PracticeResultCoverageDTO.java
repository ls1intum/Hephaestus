package de.tum.cit.aet.hephaestus.practices.result.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * How much of its scope a result read. {@code host} counts are the runner's own read receipts; {@code claimed}
 * counts were typed by a program and stay its claim.
 */
@Schema(description = "How much of its scope the review read, and what it left out")
public record PracticeResultCoverageDTO(
        @NonNull
        @Schema(
                description = "host or claimed",
                allowableValues = {"host", "claimed"})
        String source,

        @Schema(description = "Whether the review read its whole scope", requiredMode = RequiredMode.REQUIRED)
        boolean complete,

        @Nullable @Schema(description = "Reads the runner counted; host only")
        Integer reads,

        @Nullable @Schema(description = "Reads cut at their limit; host only")
        Integer truncatedReads,

        @Nullable @Schema(description = "Reads that failed; host only")
        Integer failedReads,

        @Nullable @Schema(description = "Units the program selected; claimed only")
        Integer selected,

        @Nullable @Schema(description = "Units the program checked; claimed only")
        Integer checked,

        @Nullable @Schema(description = "Units in the scope, when known; claimed only")
        Integer total,

        @NonNull @Schema(description = "What was left out, and why; empty when complete")
        List<Omission> omitted) {

    public PracticeResultCoverageDTO {
        omitted = List.copyOf(omitted);
    }

    /** One part of the scope the review did not read. */
    @Schema(name = "PracticeResultCoverageOmissionDTO", description = "One part of the scope the review did not read")
    public record Omission(
            @NonNull String what,
            @Schema(requiredMode = RequiredMode.REQUIRED) int count,
            @NonNull String reason,
            @NonNull List<String> units) {
        public Omission {
            units = List.copyOf(units);
        }
    }

    public static @Nullable PracticeResultCoverageDTO from(@Nullable JsonNode coverage) {
        if (coverage == null || !coverage.isObject()) return null;
        String source = EvidenceWarrants.text(coverage, "source");
        if (source == null) return null;
        List<Omission> omitted = new ArrayList<>();
        for (JsonNode omission : coverage.path("omitted")) {
            List<String> units = new ArrayList<>();
            omission.path("units").forEach(unit -> units.add(unit.asString()));
            omitted.add(new Omission(
                    omission.path("what").asString(""),
                    omission.path("count").asInt(0),
                    omission.path("reason").asString(""),
                    units));
        }
        return new PracticeResultCoverageDTO(
                source,
                coverage.path("complete").asBoolean(false),
                count(coverage, "reads"),
                count(coverage, "truncatedReads"),
                count(coverage, "failedReads"),
                count(coverage, "selected"),
                count(coverage, "checked"),
                count(coverage, "total"),
                omitted);
    }

    private static @Nullable Integer count(JsonNode coverage, String field) {
        JsonNode value = coverage.path(field);
        return value.isIntegralNumber() ? value.asInt() : null;
    }
}
