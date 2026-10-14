package de.tum.cit.aet.hephaestus.agent.context;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.tum.cit.aet.hephaestus.evidence.SourceCapture;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.capture.CitableText;
import de.tum.cit.aet.hephaestus.evidence.capture.WorkspaceRefusal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record JobFolderIndex(
        SourceContractVersion contractVersion,
        String catalogDigest,
        String itemKind,
        Instant capturedAt,
        List<SourceCapture> sources,
        List<WorkspaceRefusal> refusals,
        List<FolderSource> files,
        List<CitableText> citableTexts) {
    public JobFolderIndex {
        citableTexts = List.copyOf(citableTexts);
        refusals = List.copyOf(refusals);
        files = List.copyOf(files);
        var paths = new HashSet<String>();
        for (FolderSource artifact : files) {
            if (!paths.add(artifact.file().path()))
                throw new IllegalArgumentException("Duplicate folder artifact path");
        }
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        requireCapture(
                contractVersion,
                catalogDigest,
                itemKind,
                capturedAt,
                sources.stream().map(SourceCapture::kind).toList());
    }

    /** What the capture established about each source, without its staged files. */
    public Retained retained() {
        return new Retained(
                contractVersion,
                catalogDigest,
                itemKind,
                capturedAt,
                sources.stream()
                        .map(source -> new RetainedSource(source.kind(), source.state()))
                        .toList());
    }

    /**
     * A manifest as it stays on its job once admission retires the staged files: the capture's contract, kind,
     * time and each source's state and facts, read with the same checks as the whole manifest. The folder's and
     * each source's artifact and refusal inventories are not part of it, so their retirement leaves it intact; a
     * reader that needs a file's digest reads the whole manifest instead.
     */
    @JsonIgnoreProperties({"refusals", "files", "citableTexts"})
    public record Retained(
            SourceContractVersion contractVersion,
            String catalogDigest,
            String itemKind,
            Instant capturedAt,
            List<RetainedSource> sources) {
        public Retained {
            sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
            requireCapture(
                    contractVersion,
                    catalogDigest,
                    itemKind,
                    capturedAt,
                    sources.stream().map(RetainedSource::kind).toList());
        }
    }

    /** One source's retained state and facts; see {@link Retained}. */
    @JsonIgnoreProperties({"files"})
    public record RetainedSource(SourceKind kind, SourceCaptureState state) {
        public RetainedSource {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(state, "state");
        }
    }

    private static void requireCapture(
            SourceContractVersion contractVersion,
            String catalogDigest,
            String itemKind,
            Instant capturedAt,
            List<SourceKind> sourceKinds) {
        Objects.requireNonNull(contractVersion, "contractVersion");
        Objects.requireNonNull(catalogDigest, "catalogDigest");
        if (!catalogDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid catalog digest: " + catalogDigest);
        }
        Objects.requireNonNull(itemKind, "itemKind");
        Objects.requireNonNull(capturedAt, "capturedAt");
        if (sourceKinds.isEmpty()) {
            throw new IllegalArgumentException("sources must not be empty");
        }
        var kinds = new HashSet<SourceKind>();
        for (SourceKind kind : sourceKinds) {
            if (!kinds.add(Objects.requireNonNull(kind, "kind"))) {
                throw new IllegalArgumentException("Duplicate source capture: " + kind);
            }
        }
    }
}
