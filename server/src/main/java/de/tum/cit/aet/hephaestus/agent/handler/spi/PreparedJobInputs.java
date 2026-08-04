package de.tum.cit.aet.hephaestus.agent.handler.spi;

import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceManifest;
import de.tum.cit.aet.hephaestus.evidence.AutomatedAssessmentReadinessReport;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record PreparedJobInputs(
    Map<String, byte[]> files,
    @Nullable ArtifactSourceManifest artifactSourceManifest,
    @Nullable AutomatedAssessmentReadinessReport automatedAssessmentReadinessReport
) {
    public PreparedJobInputs {
        files = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(files, "files")));
        if ((artifactSourceManifest == null) != (automatedAssessmentReadinessReport == null)) {
            throw new IllegalArgumentException("Evidence manifest and readiness report must be provided together");
        }
    }

    public static PreparedJobInputs filesOnly(Map<String, byte[]> files) {
        return new PreparedJobInputs(files, null, null);
    }
}
