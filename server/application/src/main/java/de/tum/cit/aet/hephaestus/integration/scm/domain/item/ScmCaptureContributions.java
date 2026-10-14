package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.capture.CaptureContribution;
import de.tum.cit.aet.hephaestus.evidence.capture.CitableText;
import de.tum.cit.aet.hephaestus.evidence.capture.EvidenceDirectory;
import de.tum.cit.aet.hephaestus.evidence.capture.WorkspaceRefusal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ScmCaptureContributions {
    private ScmCaptureContributions() {}

    static CaptureContribution combine(List<CaptureContribution> values) {
        Map<String, byte[]> files = new HashMap<>();
        Map<SourceKind, SourceCompleteness> completeness = new HashMap<>();
        Map<SourceKind, String> identities = new HashMap<>();
        Map<SourceKind, Instant> observed = new HashMap<>(), effective = new HashMap<>();
        Map<SourceKind, SourceContentState> content = new HashMap<>();
        Map<SourceKind, SourceCaptureState> states = new HashMap<>();
        Map<String, Path> disk = new HashMap<>();
        Map<SourceKind, List<String>> limitations = new HashMap<>();
        List<EvidenceDirectory> directories = new ArrayList<>();
        List<WorkspaceRefusal> refusals = new ArrayList<>();
        List<CitableText> citable = new ArrayList<>();
        for (var value : values) {
            value.files().forEach((path, bytes) -> {
                if (files.putIfAbsent(path, bytes) != null)
                    throw new IllegalStateException("Duplicate capture path: " + path);
            });
            completeness.putAll(value.completeness());
            identities.putAll(value.immutableIdentities());
            observed.putAll(value.observedAt());
            effective.putAll(value.sourceEffectiveAt());
            content.putAll(value.contentStates());
            states.putAll(value.stateOverrides());
            disk.putAll(value.filesOnDisk());
            limitations.putAll(value.captureLimitations());
            directories.addAll(value.directories());
            refusals.addAll(value.refusals());
            citable.addAll(value.citableTexts());
        }
        return new CaptureContribution(
                files,
                completeness,
                identities,
                observed,
                effective,
                content,
                states,
                disk,
                () -> {
                    Exception failure = null;
                    for (var value : values)
                        if (value.cleanup() != null)
                            try {
                                value.cleanup().close();
                            } catch (Exception e) {
                                if (failure == null) failure = e;
                                else failure.addSuppressed(e);
                            }
                    if (failure != null) throw failure;
                },
                limitations,
                directories,
                refusals,
                citable);
    }
}
