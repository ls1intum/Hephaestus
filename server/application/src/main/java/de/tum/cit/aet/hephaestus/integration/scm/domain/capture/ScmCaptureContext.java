package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import de.tum.cit.aet.hephaestus.evidence.capture.CaptureRequest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmItemEvents;
import java.time.Instant;
import java.util.UUID;

public record ScmCaptureContext(
        ItemSnapshot pinned,
        CaptureRequest request,
        ScmCapturedWork work,
        ScmCapturePreparation preparation,
        Instant capturedAt) {
    public static final String OUTPUT_PREFIX = "context/";

    public long workspaceId() {
        return pinned.item().workspaceId();
    }

    public UUID jobId() {
        return request.jobId();
    }

    public boolean pullRequest() {
        return pinned.item().kind().equals(ScmItemEvents.PULL_REQUEST)
                || pinned.item().kind().equals(ScmItemEvents.PULL_REQUEST_REVIEW);
    }
}
