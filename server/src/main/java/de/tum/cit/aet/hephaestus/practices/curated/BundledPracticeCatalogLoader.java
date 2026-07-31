package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionValidator;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class BundledPracticeCatalogLoader {

    private static final String CATALOG_RESOURCE = "practices/default-catalog.json";

    private final JsonMapper objectMapper;

    BundledPracticeCatalogLoader(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    BundledPracticeCatalog load() {
        JsonNode catalog = readCatalog();
        JsonNode revisionNode = catalog.path("catalogRevision");
        if (!revisionNode.isIntegralNumber()) {
            throw new IllegalStateException("default practice catalog requires an integer catalogRevision");
        }
        long catalogRevision = revisionNode.asLong();
        if (catalogRevision < 1) {
            throw new IllegalStateException("default practice catalog requires catalogRevision >= 1");
        }

        List<BundledPracticeCatalog.BundledArea> areas = new ArrayList<>();
        List<BundledPracticeCatalog.BundledPractice> practices = new ArrayList<>();
        Set<String> areaSlugs = new HashSet<>();
        Set<String> practiceSlugs = new HashSet<>();
        JsonNode areasNode = catalog.path("areas");
        if (!areasNode.isArray()) {
            throw new IllegalStateException("default practice catalog areas must be an array");
        }
        for (JsonNode areaNode : areasNode) {
            String areaSlug = requiredText(areaNode, "slug");
            if (!areaSlugs.add(areaSlug)) {
                throw new IllegalStateException("duplicate bundled practice area slug: " + areaSlug);
            }
            areas.add(
                new BundledPracticeCatalog.BundledArea(
                    areaSlug,
                    requiredText(areaNode, "name"),
                    text(areaNode, "description"),
                    nonNegativeInt(areaNode, "displayOrder"),
                    text(areaNode, "icon"),
                    text(areaNode, "color")
                )
            );
            JsonNode practicesNode = areaNode.path("practices");
            if (!practicesNode.isArray()) {
                throw new IllegalStateException("bundled practice area practices must be an array: " + areaSlug);
            }
            for (JsonNode practiceNode : practicesNode) {
                String slug = requiredText(practiceNode, "slug");
                if (!practiceSlugs.add(slug)) {
                    throw new IllegalStateException("duplicate bundled practice slug: " + slug);
                }
                PracticeDefinition definition = definition(catalog, areaSlug, practiceNode, slug);
                practices.add(new BundledPracticeCatalog.BundledPractice(slug, definition, definition.digest(slug)));
            }
        }
        if (areas.isEmpty() || practices.isEmpty()) {
            throw new IllegalStateException("default practice catalog must contain areas and practices");
        }
        return new BundledPracticeCatalog(
            catalogRevision,
            catalogDigest(areas, practices),
            List.copyOf(areas),
            List.copyOf(practices)
        );
    }

    private PracticeDefinition definition(JsonNode catalog, String areaSlug, JsonNode node, String slug) {
        WorkArtifact artifactType = WorkArtifact.valueOf(requiredText(node, "artifactType"));
        JsonNode triggersNode = node.path("triggerEvents");
        if (!triggersNode.isArray()) {
            throw new IllegalStateException("bundled practice triggerEvents must be an array: " + slug);
        }
        List<String> rawTriggerEvents = new ArrayList<>();
        triggersNode.forEach(trigger -> {
            if (!trigger.isString()) {
                throw new IllegalStateException("bundled practice trigger event must be text: " + slug);
            }
            rawTriggerEvents.add(trigger.asString());
        });
        List<String> triggerEvents = rawTriggerEvents.stream().sorted().toList();
        String preambleKey = text(node, "preamble");
        if (preambleKey == null) {
            preambleKey = artifactType.name();
        }
        String criteria = composeCriteria(catalog, preambleKey, requiredText(node, "criteria"));
        String whyItMatters = text(node, "whyItMatters");
        String whatGoodLooksLike = text(node, "whatGoodLooksLike");
        PracticeDefinitionValidator.validate(artifactType, triggerEvents, whyItMatters, whatGoodLooksLike);
        return new PracticeDefinition(
            requiredText(node, "name"),
            artifactType,
            triggerEvents,
            criteria,
            loadPrecomputeScript(slug),
            whyItMatters,
            whatGoodLooksLike,
            areaSlug
        );
    }

    @Nullable
    private String loadPrecomputeScript(String slug) {
        var resource = new ClassPathResource("practices/precompute/" + slug + ".ts");
        if (!resource.exists()) {
            return null;
        }
        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read bundled precompute script: " + slug, exception);
        }
    }

    private JsonNode readCatalog() {
        try (InputStream input = new ClassPathResource(CATALOG_RESOURCE).getInputStream()) {
            return objectMapper.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read default practice catalog", exception);
        }
    }

    private static String catalogDigest(
        List<BundledPracticeCatalog.BundledArea> areas,
        List<BundledPracticeCatalog.BundledPractice> practices
    ) {
        MessageDigest digest = sha256();
        areas
            .stream()
            .sorted(Comparator.comparing(BundledPracticeCatalog.BundledArea::slug))
            .forEach(area -> {
                add(digest, area.slug());
                add(digest, area.name());
                addNullable(digest, area.description());
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(area.displayOrder()).array());
                addNullable(digest, area.icon());
                addNullable(digest, area.color());
            });
        practices
            .stream()
            .sorted(Comparator.comparing(BundledPracticeCatalog.BundledPractice::slug))
            .forEach(practice -> {
                add(digest, practice.slug());
                add(digest, practice.definitionDigest());
            });
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String composeCriteria(JsonNode catalog, String preambleKey, String criteria) {
        String preamble = requiredText(catalog.path("criteriaPreambles"), preambleKey);
        return preamble + "\n\n---\n\n" + criteria;
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode valueNode = node.get(field);
        if (valueNode == null || !valueNode.isString()) {
            throw new IllegalStateException("bundled catalog field must be text: " + field);
        }
        String value = valueNode.asString();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("bundled catalog field is required: " + field);
        }
        return value;
    }

    @Nullable
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw new IllegalStateException("bundled catalog field must be text or null: " + field);
        }
        String text = value.asString();
        return text.isBlank() ? null : text;
    }

    private static int nonNegativeInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 0) {
            throw new IllegalStateException("bundled catalog field must be a non-negative integer: " + field);
        }
        return value.asInt();
    }

    private static void addNullable(MessageDigest digest, @Nullable String value) {
        if (value == null) {
            digest.update((byte) 0);
        } else {
            digest.update((byte) 1);
            add(digest, value);
        }
    }

    private static void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
