package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.context.JobFolderIndex;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.capture.CitableText;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

/** The canonical captured-file inventory; domain version strings remain opaque. */
final class CapturedEvidence {
    record FileProof(SourceKind kind, String sha256) {}

    private final JobFolderIndex manifest;
    private final Set<SourceKind> available;
    private final Map<String, FileProof> files;

    private CapturedEvidence(JobFolderIndex manifest) {
        this.manifest = manifest;
        var sources = new HashSet<SourceKind>();
        manifest.sources().forEach(source -> {
            if (source.state() instanceof SourceCaptureState.Available) sources.add(source.kind());
        });
        var indexed = new HashMap<String, FileProof>();
        for (var entry : manifest.files()) {
            if (indexed.put(
                            entry.file().path(),
                            new FileProof(entry.kind(), entry.file().sha256()))
                    != null) {
                throw new JobDeliveryException("A captured path belongs to multiple sources");
            }
        }
        available = Set.copyOf(sources);
        files = Map.copyOf(indexed);
    }

    static CapturedEvidence of(JobFolderIndex manifest) {
        return new CapturedEvidence(manifest);
    }

    static CapturedEvidence of(ReviewJob job, ObjectMapper mapper) {
        var capture = job.getCapture();
        if (capture == null || !capture.path("manifest").isObject()) {
            throw new JobDeliveryException("The attempt has no captured source manifest");
        }
        return of(mapper.treeToValue(capture.get("manifest"), JobFolderIndex.class));
    }

    SourceContractVersion contractVersion() {
        return manifest.contractVersion();
    }

    Set<SourceKind> availableSources() {
        return available;
    }

    Map<String, FileProof> files() {
        return files;
    }

    @Nullable
    FileProof file(String path) {
        return files.get(path);
    }

    CitableText resolve(SourceKind kind, String path, CitationSide side, @Nullable String revision) {
        var matches = manifest.citableTexts().stream()
                .filter(text -> text.source().equals(kind.value())
                        && text.path().equals(path)
                        && text.side() == side
                        && Objects.equals(text.revision(), revision))
                .toList();
        if (!available.contains(kind) || matches.size() != 1) {
            throw new CandidateEvidenceVerifier.Unverified(
                    "quote_unverified", "The citation has no unique captured text", null);
        }
        return matches.getFirst();
    }

    FileProof requireFile(SourceKind kind, String path) {
        var file = files.get(path);
        if (!available.contains(kind) || file == null || !file.kind().equals(kind)) {
            throw new JobDeliveryException("The cited source was not captured at this path");
        }
        return file;
    }
}
