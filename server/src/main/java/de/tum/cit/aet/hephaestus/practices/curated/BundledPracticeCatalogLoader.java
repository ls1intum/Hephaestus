package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionValidator;
import de.tum.cit.aet.hephaestus.practices.curated.BundledPracticeCatalog.BundledEntry;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the catalog shipped on the classpath.
 *
 * <p>Parsed once at construction: the classpath cannot change while the process runs, and every read
 * of the effective catalog composes this with the override rows, so it is asked for often.
 */
@Component
public class BundledPracticeCatalogLoader {

    private static final String CATALOG_RESOURCE = "practices/default-catalog.json";

    private final BundledPracticeCatalog catalog;

    BundledPracticeCatalogLoader(JsonMapper objectMapper) {
        this.catalog = parse(objectMapper);
    }

    BundledPracticeCatalog catalog() {
        return catalog;
    }

    private static BundledPracticeCatalog parse(JsonMapper objectMapper) {
        JsonNode root = readCatalog(objectMapper);
        List<BundledEntry<AreaDefinition>> areas = new ArrayList<>();
        List<BundledEntry<PracticeDefinition>> practices = new ArrayList<>();
        Set<String> areaSlugs = new HashSet<>();
        Set<String> practiceSlugs = new HashSet<>();
        JsonNode areasNode = root.path("areas");
        if (!areasNode.isArray()) {
            throw new IllegalStateException("default practice catalog areas must be an array");
        }
        for (JsonNode areaNode : areasNode) {
            String areaSlug = requiredText(areaNode, "slug");
            if (!areaSlugs.add(areaSlug)) {
                throw new IllegalStateException("duplicate bundled practice area slug: " + areaSlug);
            }
            areas.add(
                new BundledEntry<>(
                    areaSlug,
                    new AreaDefinition(
                        requiredText(areaNode, "name"),
                        text(areaNode, "description"),
                        nonNegativeInt(areaNode, "displayOrder"),
                        text(areaNode, "icon"),
                        text(areaNode, "color")
                    )
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
                practices.add(new BundledEntry<>(slug, definition(root, areaSlug, practiceNode, slug)));
            }
        }
        if (areas.isEmpty() || practices.isEmpty()) {
            throw new IllegalStateException("default practice catalog must contain areas and practices");
        }
        return new BundledPracticeCatalog(List.copyOf(areas), List.copyOf(practices));
    }

    private static PracticeDefinition definition(JsonNode catalog, String areaSlug, JsonNode node, String slug) {
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

    private static @Nullable String loadPrecomputeScript(String slug) {
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

    private static JsonNode readCatalog(JsonMapper objectMapper) {
        try (InputStream input = new ClassPathResource(CATALOG_RESOURCE).getInputStream()) {
            return objectMapper.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read default practice catalog", exception);
        }
    }

    private static String composeCriteria(JsonNode catalog, String preambleKey, String criteria) {
        return requiredText(catalog.path("criteriaPreambles"), preambleKey) + "\n\n---\n\n" + criteria;
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

    private static @Nullable String text(JsonNode node, String field) {
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
}
