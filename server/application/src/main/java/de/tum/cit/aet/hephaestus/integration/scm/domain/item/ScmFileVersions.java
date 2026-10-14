package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import tools.jackson.databind.ObjectMapper;

@Component
public class ScmFileVersions {
    public record Entry(String path, String mode, String objectId) {}

    private final ObjectMapper mapper;

    public ScmFileVersions(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ItemVersion version(
            String assignedPath,
            String blobId,
            String mode,
            List<String> globs,
            List<Entry> inventory,
            String repositoryVersion) {
        requireObjectId(blobId);
        requireObjectId(repositoryVersion);
        List<String> context = globs.stream().distinct().sorted().toList();
        var matcher = new AntPathMatcher("/");
        context.forEach(ScmFileVersions::validateGlob);
        List<Entry> entries = inventory.stream()
                .filter(e -> !e.path().equals(assignedPath))
                .filter(e -> context.stream().anyMatch(glob -> matcher.match(glob, e.path())))
                .sorted(Comparator.comparing(Entry::path))
                .toList();
        // The wire contract fixes this key order; ObjectNode preserves the insertion order.
        var canonical = mapper.createObjectNode();
        canonical.put("scheme", "scm.file:v1");
        canonical.put("mode", mode);
        var contexts = canonical.putArray("context");
        context.forEach(contexts::add);
        var rows = canonical.putArray("entries");
        for (Entry entry : entries) {
            var row = rows.addObject();
            row.put("path", entry.path());
            row.put("mode", entry.mode());
            row.put("objectId", entry.objectId());
        }
        byte[] bytes = mapper.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8);
        return new ItemVersion("scm.file:v1:" + blobId + ":" + ScmVersion.contentHash(bytes), repositoryVersion);
    }

    private static void requireObjectId(String value) {
        if (!value.matches("[0-9a-f]{40}|[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid Git object id");
    }

    private static void validateGlob(String glob) {
        if (glob.isBlank()
                || glob.startsWith("/")
                || glob.contains("\\")
                || List.of(glob.split("/", -1)).contains(".."))
            throw new IllegalArgumentException("Invalid context glob");
    }
}
