package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import de.tum.cit.aet.hephaestus.evidence.capture.CaptureRequest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
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
        return pinned.item().kind().value().equals("scm.pull_request")
                || pinned.item().kind().value().equals("scm.pull_request_review");
    }
}
