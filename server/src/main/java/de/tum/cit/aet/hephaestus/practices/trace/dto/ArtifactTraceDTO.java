package de.tum.cit.aet.hephaestus.practices.trace.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * The whole answer to "what did Hephaestus do about this piece of work, and why".
 *
 * <p>Every practice the workspace runs against this kind of work appears, including — especially — the
 * ones that did nothing. A listing that showed only what happened would be the failure mode this view
 * exists to remove, and is why Renovate's dashboard enumerates what it is <em>not</em> doing and
 * GitHub's rule insights show what <em>would</em> have failed.
 */
@Schema(description = "Every practice's answer for one artifact, and the occurrences those answers rest on")
public record ArtifactTraceDTO(
    @NonNull ArtifactKind artifactKind,
    @NonNull Long artifactId,
    @NonNull
    @Schema(description = "The label a person recognises; the kind's display name when the mirror cannot name it")
    String title,
    @Schema(description = "The number the provider shows, for kinds that have one") Integer number,
    @Schema(description = "Repository, collection or channel it sits in") String container,
    @Schema(description = "Where to open it upstream; absent for a deleted or unlinkable artifact") String url,
    @NonNull
    @Schema(description = "Everything recorded about this artifact, oldest first")
    List<TracedSignalDTO> signals,
    @NonNull
    @Schema(
        description = "Every practice this workspace runs against this kind of work, answered ones first, " +
            "then the quiet ones"
    )
    List<PracticeTraceEntryDTO> practices
) {}
