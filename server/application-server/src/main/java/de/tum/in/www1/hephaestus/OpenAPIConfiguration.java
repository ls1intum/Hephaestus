package de.tum.in.www1.hephaestus;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Hephaestus API",
        description = "API documentation for the Hephaestus application server.",
        version = "0.9.0-rc.5",
        contact = @Contact(name = "Felix T.J. Dietrich", email = "felixtj.dietrich@tum.de"),
        license = @License(name = "MIT License", url = "https://github.com/ls1intum/Hephaestus/blob/develop/LICENSE")
    ),
    servers = { @Server(url = "/", description = "Default Server URL") }
)
public class OpenAPIConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(OpenAPIConfiguration.class);

    /**
     * List of intelligence service model names that should be imported from the intelligence service OpenAPI spec
     * and should preserve their DTO suffix in schema references.
     */
    private static final List<String> INTELLIGENCE_SERVICE_MODELS = List.of(
        "UIMessage",
        "UIMessagePart",
        "TextUIPart",
        "ReasoningUIPart",
        "ToolInputStreamingPart",
        "ToolInputAvailablePart",
        "ToolOutputAvailablePart",
        "ToolOutputErrorPart",
        "SourceUrlUIPart",
        "SourceDocumentUIPart",
        "FileUIPart",
        "DataUIPart",
        "StepStartUIPart"
        // Nested tool input / output schemas are automatically included
    );

    /**
     * List of domain object names that should be included in the schema even though they don't end with DTO.
     */
    private static final List<String> ALLOWED_DOMAIN_OBJECTS = List.of("PageableObject", "SortObject");

    @Bean
    public OpenApiCustomizer schemaCustomizer() {
        return openApi -> {
            var components = openApi.getComponents();

            if (components != null && components.getSchemas() != null) {
                // Create a new map to hold filtered schemas
                @SuppressWarnings("rawtypes")
                Map<String, Schema> filteredSchemas = new HashMap<>();

                // Include schemas with DTO suffix and remove the suffix
                var dtoSchemas = components
                    .getSchemas()
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getKey().endsWith("DTO"))
                    .collect(
                        Collectors.toMap(
                            entry -> entry.getKey().substring(0, entry.getKey().length() - 3),
                            entry -> {
                                var schema = entry.getValue();
                                schema.setName(entry.getKey().substring(0, entry.getKey().length() - 3));
                                return schema;
                            }
                        )
                    );

                filteredSchemas.putAll(dtoSchemas);

                // Include allowed domain objects (PageableObject, SortObject)
                var allowedDomainObjects = components
                    .getSchemas()
                    .entrySet()
                    .stream()
                    .filter(entry -> ALLOWED_DOMAIN_OBJECTS.contains(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                filteredSchemas.putAll(allowedDomainObjects);

                // Remove DTO suffix from attribute names in all schemas
                filteredSchemas.forEach((key, value) -> {
                    @SuppressWarnings({ "rawtypes", "unchecked" })
                    Map<String, Schema> properties = value.getProperties();
                    if (properties != null) {
                        properties.forEach((propertyKey, propertyValue) -> {
                            removeDTOSuffixesFromSchemaRecursively(propertyValue);
                        });
                    }
                });

                components.setSchemas(filteredSchemas);

                // Load and add intelligence service schemas (UIMessage, UIMessagePart, etc.)
                Map<String, Schema<?>> intelligenceSchemas = loadIntelligenceServiceUISchemas();
                if (!intelligenceSchemas.isEmpty()) {
                    filteredSchemas.putAll(intelligenceSchemas);
                    logger.info("Added {} intelligence service schemas to OpenAPI spec", intelligenceSchemas.size());
                }
            } else {
                logger.warn("Components or Schemas are null in OpenAPI configuration.");
            }

            var paths = openApi.getPaths();
            if (paths != null) {
                paths.forEach((path, pathItem) -> {
                    logger.debug("Processing path: {}", path);
                    pathItem
                        .readOperations()
                        .forEach(operation -> {
                            // Remove DTO suffix from response schemas
                            var responses = operation.getResponses();
                            if (responses != null) {
                                responses.forEach((responseCode, response) -> {
                                    var content = response.getContent();
                                    if (content != null) {
                                        content.forEach((contentType, mediaType) -> {
                                            if (mediaType != null && mediaType.getSchema() != null) {
                                                removeDTOSuffixesFromSchemaRecursively(mediaType.getSchema());
                                            } else {
                                                logger.warn(
                                                    "MediaType or Schema is null for content type: {}",
                                                    contentType
                                                );
                                            }
                                        });
                                    } else {
                                        logger.warn("Response with code {} has no content.", responseCode);
                                    }
                                });
                            }
                            if (operation.getRequestBody() != null) {
                                var requestBodyContent = operation.getRequestBody().getContent();
                                requestBodyContent.forEach((contentType, mediaType) -> {
                                    removeDTOSuffixesFromSchemaRecursively(mediaType.getSchema());
                                });
                            }

                            // Remove -controller suffix from tags
                            if (operation.getTags() != null) {
                                operation.setTags(
                                    operation
                                        .getTags()
                                        .stream()
                                        .filter(tag -> {
                                            if (tag.length() > 11) {
                                                return true;
                                            } else {
                                                logger.warn(
                                                    "Tag '{}' is shorter than expected and cannot be trimmed.",
                                                    tag
                                                );
                                                return false;
                                            }
                                        })
                                        .map(tag -> tag.substring(0, tag.length() - 11))
                                        .collect(Collectors.toList())
                                );
                            }
                        });
                });
            } else {
                logger.warn("Paths are null in OpenAPI configuration.");
            }
        };
    }

    /**
     * Load schemas from the intelligence service OpenAPI spec.
     * This includes UIMessage, UIMessagePart, and Stream* models.
     */
    private Map<String, Schema<?>> loadIntelligenceServiceUISchemas() {
        Map<String, Schema<?>> intelligenceSchemas = new HashMap<>();

        try {
            // Path to the intelligence service OpenAPI spec
            File intelligenceSpecFile = new File("../intelligence-service/openapi.yaml");

            if (intelligenceSpecFile.exists()) {
                var intelligenceOpenApi = new OpenAPIV3Parser().read(intelligenceSpecFile.getAbsolutePath());

                if (
                    intelligenceOpenApi != null &&
                    intelligenceOpenApi.getComponents() != null &&
                    intelligenceOpenApi.getComponents().getSchemas() != null
                ) {
                    @SuppressWarnings("unchecked")
                    Map<String, Schema<?>> allSchemas = (Map<String, Schema<?>>) (Map<?, ?>) intelligenceOpenApi
                        .getComponents()
                        .getSchemas();

                    // Seed with explicitly requested models, then include all referenced schemas recursively
                    Set<String> toInclude = new LinkedHashSet<>(INTELLIGENCE_SERVICE_MODELS);
                    Set<String> visited = new HashSet<>();

                    for (String name : new LinkedHashSet<>(toInclude)) {
                        includeSchemaWithReferences(name, allSchemas, intelligenceSchemas, toInclude, visited);
                    }

                    logger.info(
                        "Loaded {} schemas (including nested references) from intelligence service",
                        intelligenceSchemas.size()
                    );
                } else {
                    logger.warn("Could not load schemas from intelligence service OpenAPI spec");
                }
            } else {
                logger.warn(
                    "Intelligence service OpenAPI spec not found at: {}",
                    intelligenceSpecFile.getAbsolutePath()
                );
            }
        } catch (Exception e) {
            logger.error("Error loading intelligence service schemas: {}", e.getMessage(), e);
        }

        return intelligenceSchemas;
    }

    /**
     * Include a schema by name and all of its referenced component schemas recursively.
     */
    private void includeSchemaWithReferences(
        String name,
        Map<String, Schema<?>> allSchemas,
        Map<String, Schema<?>> out,
        Set<String> toInclude,
        Set<String> visited
    ) {
        if (name == null || visited.contains(name)) {
            return;
        }
        Schema<?> schema = allSchemas.get(name);
        if (schema == null) {
            return;
        }
        visited.add(name);
        out.put(name, schema);
        logger.debug("Loaded intelligence service schema: {}", name);

        // Discover referenced component schema names and include them as well.
        Set<String> refs = new HashSet<>();
        collectRefs(schema, refs);
        for (String refName : refs) {
            if (!visited.contains(refName)) {
                toInclude.add(refName);
                includeSchemaWithReferences(refName, allSchemas, out, toInclude, visited);
            }
        }
    }

    /**
     * Collect component schema names referenced via $ref recursively within the given schema.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void collectRefs(Schema schema, Set<String> out) {
        if (schema == null) {
            return;
        }
        // Direct $ref
        if (schema.get$ref() != null) {
            String ref = schema.get$ref();
            String name = ref.substring(ref.lastIndexOf('/') + 1);
            out.add(name);
        }
        // Properties
        Map<String, Schema> props = (Map<String, Schema>) schema.getProperties();
        if (props != null) {
            for (Schema prop : props.values()) {
                collectRefs(prop, out);
            }
        }
        // Items (arrays)
        if (schema.getItems() != null) {
            collectRefs(schema.getItems(), out);
        }
        // Composed schemas: allOf, anyOf, oneOf
        if (schema.getAllOf() != null) {
            for (Object s : schema.getAllOf()) {
                collectRefs((Schema) s, out);
            }
        }
        if (schema.getAnyOf() != null) {
            for (Object s : schema.getAnyOf()) {
                collectRefs((Schema) s, out);
            }
        }
        if (schema.getOneOf() != null) {
            for (Object s : schema.getOneOf()) {
                collectRefs((Schema) s, out);
            }
        }
    }

    private void removeDTOSuffixesFromSchemaRecursively(Schema<?> schema) {
        if (schema == null) {
            return;
        }

        if (schema.get$ref() != null && schema.get$ref().endsWith("DTO")) {
            String originalRef = schema.get$ref();
            String schemaName = originalRef.substring(originalRef.lastIndexOf("/") + 1);

            // Check if this is an intelligence service model (without DTO suffix)
            String nameWithoutDTO = schemaName.substring(0, schemaName.length() - 3);
            if (INTELLIGENCE_SERVICE_MODELS.contains(nameWithoutDTO)) {
                // Keep the original reference with DTO suffix for intelligence service models
                logger.debug("Preserving intelligence service model reference: {}", originalRef);
            } else {
                // Remove DTO suffix for regular DTOs
                String newRef = originalRef.substring(0, originalRef.length() - 3);
                schema.set$ref(newRef);
                logger.debug("Updated $ref from {} to {}", originalRef, newRef);
            }
        }

        if (schema.getItems() != null) {
            removeDTOSuffixesFromSchemaRecursively(schema.getItems());
        }
    }
}
