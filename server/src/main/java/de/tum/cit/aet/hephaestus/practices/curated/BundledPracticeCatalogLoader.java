package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.AreaDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedReviewPolicy;
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

@Component
public class BundledPracticeCatalogLoader {

    private static final String CATALOG_RESOURCE = "practices/default-catalog.json";

    private final BundledPracticeCatalog catalog;

    BundledPracticeCatalogLoader(JsonMapper objectMapper, PracticeDefinitionValidator definitionValidator) {
        this.catalog = parse(objectMapper, definitionValidator);
    }

    BundledPracticeCatalog catalog() {
        return catalog;
    }

    private static BundledPracticeCatalog parse(
        JsonMapper objectMapper,
        PracticeDefinitionValidator definitionValidator
    ) {
        JsonNode root = readCatalog(objectMapper);
        List<BundledEntry<AreaDefinition>> areas = new ArrayList<>();
        List<BundledEntry<PracticeDefinition>> practices = new ArrayList<>();
        Set<String> areaSlugs = new HashSet<>();
        Set<String> practiceSlugs = new HashSet<>();
        JsonNode areasNode = root.path("areas");
        if (!areasNode.isArray()) {
            throw new IllegalStateException("default practice catalog areas must be an array");
        }
        int areaPosition = 0;
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
                        text(areaNode, "icon"),
                        text(areaNode, "color")
                    ),
                    areaPosition++
                )
            );

            JsonNode practicesNode = areaNode.path("practices");
            if (!practicesNode.isArray()) {
                throw new IllegalStateException("bundled practice area practices must be an array: " + areaSlug);
            }
            int practicePosition = 0;
            for (JsonNode practiceNode : practicesNode) {
                String slug = requiredText(practiceNode, "slug");
                if (!practiceSlugs.add(slug)) {
                    throw new IllegalStateException("duplicate bundled practice slug: " + slug);
                }
                practices.add(
                    new BundledEntry<>(
                        slug,
                        definition(objectMapper, definitionValidator, root, areaSlug, practiceNode, slug),
                        practicePosition++
                    )
                );
            }
        }
        if (areas.isEmpty() || practices.isEmpty()) {
            throw new IllegalStateException("default practice catalog must contain areas and practices");
        }
        return new BundledPracticeCatalog(List.copyOf(areas), List.copyOf(practices));
    }

    private static PracticeDefinition definition(
        JsonMapper objectMapper,
        PracticeDefinitionValidator definitionValidator,
        JsonNode catalog,
        String areaSlug,
        JsonNode node,
        String slug
    ) {
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
        PracticeDefinition definition = new PracticeDefinition(
            requiredText(node, "name"),
            artifactType,
            triggerEvents,
            criteria,
            loadPrecomputeScript(node, slug),
            automatedReviewPolicy(objectMapper, catalog, node, slug),
            whyItMatters,
            whatGoodLooksLike,
            areaSlug
        );
        definitionValidator.validate(definition);
        return definition;
    }

    private static PracticeAutomatedReviewPolicy automatedReviewPolicy(
        JsonMapper objectMapper,
        JsonNode catalog,
        JsonNode node,
        String slug
    ) {
        String automatedReviewPolicyId = requiredText(node, "automatedReviewPolicyId");
        JsonNode automatedReviewPolicy = catalog.path("automatedReviewPolicy").get(automatedReviewPolicyId);
        if (automatedReviewPolicy == null || !automatedReviewPolicy.isObject()) {
            throw new IllegalStateException(
                "unknown bundled practice automated-review policy: " + automatedReviewPolicyId
            );
        }
        try {
            return objectMapper.treeToValue(automatedReviewPolicy, PracticeAutomatedReviewPolicy.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("invalid bundled practice evidence: " + slug, exception);
        }
    }

    private static @Nullable String loadPrecomputeScript(JsonNode node, String slug) {
        String resourcePath = text(node, "precomputeScript");
        if (resourcePath == null) {
            return null;
        }
        var resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IllegalStateException("bundled precompute script does not exist: " + resourcePath);
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
}
