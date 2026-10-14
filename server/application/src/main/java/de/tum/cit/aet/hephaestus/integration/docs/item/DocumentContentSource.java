package de.tum.cit.aet.hephaestus.integration.docs.item;

import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceReason;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.capture.CaptureContribution;
import de.tum.cit.aet.hephaestus.evidence.capture.CaptureRequest;
import de.tum.cit.aet.hephaestus.evidence.capture.CitableText;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class DocumentContentSource {
    public static final SourceKind SOURCE = new SourceKind("docs.document.core");
    public static final String BODY_KEY = "inputs/context/document.md";
    public static final String METADATA_KEY = "inputs/context/document.json";

    private final ObjectMapper mapper;

    public DocumentContentSource(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public CaptureContribution capture(
            CaptureRequest request,
            ItemVersion version,
            @Nullable String body,
            byte[] metadata,
            @Nullable Instant effectiveAt,
            SourceAbsenceReason absence) {
        if (!request.sources().contains(SOURCE)) {
            return CaptureContribution.empty();
        }
        if (body == null) {
            return new CaptureContribution(
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(SOURCE, new SourceCaptureState.Unavailable(absence)),
                    Map.of(),
                    null,
                    Map.of(),
                    List.of(),
                    List.of(),
                    List.of());
        }
        var files = new LinkedHashMap<String, byte[]>();
        var texts = new ArrayList<CitableText>();
        register(files, texts, BODY_KEY, "context/document.md", body.getBytes(StandardCharsets.UTF_8));
        register(files, texts, METADATA_KEY, "context/document.json", metadata);
        registerRecordStrings(
                Objects.requireNonNull(mapper.readTree(metadata), "A document record is required"), "", files, texts);
        return new CaptureContribution(
                files,
                Map.of(SOURCE, SourceCompleteness.COMPLETE),
                Map.of(SOURCE, version.value()),
                Map.of(SOURCE, Instant.now()),
                effectiveAt == null ? Map.of() : Map.of(SOURCE, effectiveAt),
                Map.of(SOURCE, body.isEmpty() ? SourceContentState.EMPTY : SourceContentState.NON_EMPTY),
                Map.of(),
                Map.of(),
                null,
                Map.of(),
                List.of(),
                List.of(),
                texts);
    }

    private static void registerRecordStrings(
            JsonNode node, String pointer, Map<String, byte[]> files, List<CitableText> texts) {
        if (node.isString()) {
            register(
                    files,
                    texts,
                    "inputs/context/document_texts/" + texts.size() + ".txt",
                    "context/document.json#" + pointer,
                    node.asString().getBytes(StandardCharsets.UTF_8));
        } else if (node.isObject()) {
            node.properties()
                    .forEach(field -> registerRecordStrings(
                            field.getValue(),
                            pointer + "/" + field.getKey().replace("~", "~0").replace("/", "~1"),
                            files,
                            texts));
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++)
                registerRecordStrings(Objects.requireNonNull(node.get(index)), pointer + "/" + index, files, texts);
        }
    }

    private static void register(
            Map<String, byte[]> files, List<CitableText> texts, String stagedPath, String logicalPath, byte[] bytes) {
        files.put(stagedPath, bytes);
        try {
            String digest = HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            texts.add(new CitableText(SOURCE.value(), logicalPath, CitationSide.NEW, null, stagedPath, digest));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }
}
