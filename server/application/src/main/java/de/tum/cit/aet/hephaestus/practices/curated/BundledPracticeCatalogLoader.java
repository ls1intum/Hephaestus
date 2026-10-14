package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.GroupDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinitionValidator;
import de.tum.cit.aet.hephaestus.practices.PracticeGuidanceRules;
import de.tum.cit.aet.hephaestus.practices.catalog.CatalogPracticeDocument;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@ConditionalOnServerRole
public class BundledPracticeCatalogLoader {
    private final BundledPracticeCatalog catalog;

    public BundledPracticeCatalogLoader(JsonMapper mapper, PracticeDefinitionValidator validator) {
        JsonNode root = mapper.readTree(read("practices/default-catalog.json"));
        var groups = new ArrayList<BundledPracticeCatalog.BundledEntry<GroupDefinition>>();
        var practices = new ArrayList<BundledPracticeCatalog.BundledEntry<CatalogPracticeDocument>>();
        var groupSlugs = new HashSet<String>();
        var slugs = new HashSet<String>();
        if (!root.path("groups").isArray()) throw new IllegalArgumentException("The bundled catalog needs groups.");
        int groupPosition = 0;
        for (JsonNode group : root.path("groups")) {
            String groupSlug = required(group, "slug");
            if (!groupSlugs.add(groupSlug)) throw new IllegalArgumentException("Duplicate catalog group slug.");
            var groupDefinition = new GroupDefinition(
                    required(group, "name"),
                    optional(group, "description"),
                    optional(group, "icon"),
                    optional(group, "color"));
            groups.add(new BundledPracticeCatalog.BundledEntry<>(groupSlug, groupDefinition, groupPosition++));
            if (!group.path("practices").isArray())
                throw new IllegalArgumentException("A catalog group needs practices.");
            int position = 0;
            for (JsonNode practice : group.path("practices")) {
                String slug = required(practice, "slug");
                if (!slugs.add(slug)) throw new IllegalArgumentException("Duplicate catalog practice slug.");
                ItemKind kind = new ItemKind(required(practice, "kind"));
                JsonNode raw = practice.path("definition").deepCopy();
                if (!(raw instanceof ObjectNode definition))
                    throw new IllegalArgumentException("A bundled practice needs a definition.");
                String script = optional(practice, "scriptResource");
                if (script != null) {
                    if (!script.startsWith("practices/scripts/") || script.contains(".."))
                        throw new IllegalArgumentException("Invalid script resource.");
                    definition.put("script", read(script));
                } else if (!definition.has("script")) {
                    definition.putNull("script");
                }
                String preamble = optional(practice, "preamble");
                if (preamble != null)
                    definition.put(
                            "criteria",
                            required(root.path("criteriaPreambles"), preamble)
                                    + "\n\n---\n\n"
                                    + required(definition, "criteria"));
                if (!(definition.path("guidance") instanceof ObjectNode guidance))
                    throw new IllegalArgumentException("A bundled practice needs guidance.");
                JsonNode visual = practice.get("visualResource");
                if (visual != null && !visual.isNull()) {
                    var rendered = mapper.createObjectNode();
                    rendered.put("svg", guidanceResource(slug, required(visual, "file")));
                    rendered.put("alt", required(visual, "alt"));
                    guidance.set("visual", rendered);
                }
                String guide = optional(practice, "guideResource");
                if (guide != null) {
                    String markdown = guidanceResource(slug, guide);
                    var figures = mapper.createObjectNode();
                    for (String name : PracticeGuidanceRules.figureNames(markdown)) {
                        figures.put(
                                name,
                                guidanceResource(slug, "practices/guidance/" + slug + "/figures/" + name + ".svg"));
                    }
                    var rendered = mapper.createObjectNode();
                    rendered.put("markdown", markdown);
                    rendered.set("figures", figures);
                    guidance.set("guide", rendered);
                }
                PracticeDefinition authored = mapper.treeToValue(definition, PracticeDefinition.class);
                validator.validate(kind, authored);
                practices.add(new BundledPracticeCatalog.BundledEntry<>(
                        slug, new CatalogPracticeDocument(kind, groupSlug, authored), position++));
            }
        }
        catalog = new BundledPracticeCatalog(groups, practices);
    }

    public BundledPracticeCatalog catalog() {
        return catalog;
    }

    private static String guidanceResource(String slug, String path) {
        if (!path.startsWith("practices/guidance/" + slug + "/") || path.contains("..")) {
            throw new IllegalArgumentException("Guidance resources must belong to the practice.");
        }
        return read(path);
    }

    private static String read(String path) {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read the bundled catalog resource: " + path, exception);
        }
    }

    private static String required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException("The catalog needs a text value for " + field + ".");
        }
        return value.asString();
    }

    private static @Nullable String optional(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : required(node, field);
    }
}
