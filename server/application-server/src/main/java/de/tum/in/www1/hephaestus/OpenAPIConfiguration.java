package de.tum.in.www1.hephaestus;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI configuration that:
 * 1. Processes application-server DTOs (removes "DTO" suffix)
 * 2. Imports tagged schemas and paths from intelligence-service
 *
 * Intelligence-service schemas/paths are included if they have:
 *   x-hephaestus:
 *     export: true
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Hephaestus API",
        description = "API documentation for the Hephaestus application server.",
        version = "0.13.1",
        contact = @Contact(name = "Felix T.J. Dietrich", email = "felixtj.dietrich@tum.de"),
        license = @License(name = "MIT License", url = "https://github.com/ls1intum/Hephaestus/blob/develop/LICENSE")
    ),
    servers = { @Server(url = "/", description = "Default Server URL") },
    security = { @SecurityRequirement(name = "bearerAuth") }
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT authentication via Keycloak. Obtain a token from your Keycloak instance."
)
public class OpenAPIConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OpenAPIConfiguration.class);
    private static final String INTELLIGENCE_SERVICE_SPEC = "../intelligence-service/openapi.yaml";
    private static final String WORKSPACE_PATH_PREFIX = "/workspaces/{workspaceSlug}";

    /** Domain objects to include even without DTO suffix */
    private static final List<String> ALLOWED_DOMAIN_OBJECTS = List.of("PageableObject", "SortObject");

    @Bean
    public OpenApiCustomizer schemaCustomizer() {
        return openApi -> {
            processApplicationServerSchemas(openApi);
            importIntelligenceServiceSpec(openApi);
            processAllPaths(openApi);
        };
    }

    /**
     * Process application-server schemas: include DTOs and remove suffix.
     */
    private void processApplicationServerSchemas(OpenAPI openApi) {
        var components = openApi.getComponents();
        if (components == null || components.getSchemas() == null) {
            log.warn("No schemas found in application-server spec");
            return;
        }

        @SuppressWarnings("rawtypes")
        Map<String, Schema> filteredSchemas = new HashMap<>();

        // Include DTOs with suffix removed
        components
            .getSchemas()
            .entrySet()
            .stream()
            .filter(e -> e.getKey().endsWith("DTO"))
            .forEach(e -> {
                String nameWithoutDto = e.getKey().substring(0, e.getKey().length() - 3);
                e.getValue().setName(nameWithoutDto);
                filteredSchemas.put(nameWithoutDto, e.getValue());
            });

        // Include allowed domain objects
        components
            .getSchemas()
            .entrySet()
            .stream()
            .filter(e -> ALLOWED_DOMAIN_OBJECTS.contains(e.getKey()))
            .forEach(e -> filteredSchemas.put(e.getKey(), e.getValue()));

        // Update $ref to remove DTO suffix
        filteredSchemas.values().forEach(this::removeDtoSuffixFromRefs);

        components.setSchemas(filteredSchemas);
    }

    /**
     * Import schemas and paths from intelligence-service that are tagged for export.
     */
    @SuppressWarnings("unchecked")
    private void importIntelligenceServiceSpec(OpenAPI openApi) {
        File specFile = new File(INTELLIGENCE_SERVICE_SPEC);
        if (!specFile.exists()) {
            log.warn("Intelligence service spec not found at: {}", specFile.getAbsolutePath());
            return;
        }

        OpenAPI intelligenceSpec = new OpenAPIV3Parser().read(specFile.getAbsolutePath());
        if (intelligenceSpec == null) {
            log.error("Failed to parse intelligence service spec");
            return;
        }

        if (intelligenceSpec.getComponents() == null || intelligenceSpec.getComponents().getSchemas() == null) {
            log.warn("No schemas found in intelligence-service spec");
            return;
        }

        Map<String, Schema<?>> allIntelligenceSchemas = (Map<String, Schema<?>>) (Map<?, ?>) intelligenceSpec
            .getComponents()
            .getSchemas();

        // Collect all schema names we need to import (from tagged schemas AND from path references)
        Set<String> schemasToImport = new HashSet<>();

        // 1. Find schemas explicitly tagged for export
        for (var entry : allIntelligenceSchemas.entrySet()) {
            if (isTaggedForExport(entry.getValue())) {
                schemasToImport.add(entry.getKey());
            }
        }

        // 2. Import tagged paths and collect all schemas they reference
        int importedPaths = 0;
        if (intelligenceSpec.getPaths() != null) {
            for (var entry : intelligenceSpec.getPaths().entrySet()) {
                String path = entry.getKey();
                PathItem pathItem = entry.getValue();

                boolean shouldExport = pathItem.readOperations().stream().anyMatch(this::isTaggedForExport);

                if (shouldExport) {
                    // Collect all schemas referenced by this path's operations
                    for (var operation : pathItem.readOperations()) {
                        collectSchemasFromOperation(operation, schemasToImport);
                    }

                    // Rewrite path to include workspace prefix
                    String newPath = WORKSPACE_PATH_PREFIX + path;
                    openApi.getPaths().addPathItem(newPath, pathItem);
                    importedPaths++;
                }
            }
        }

        // 3. Recursively include all schemas referenced by the schemas we're importing
        Set<String> visited = new HashSet<>();
        for (String name : new HashSet<>(schemasToImport)) {
            collectReferencedSchemas(name, allIntelligenceSchemas, schemasToImport, visited);
        }

        // 4. Add all collected schemas to openApi
        var targetSchemas = openApi.getComponents().getSchemas();
        for (String name : schemasToImport) {
            Schema<?> schema = allIntelligenceSchemas.get(name);
            if (schema != null) {
                targetSchemas.put(name, schema);
            }
        }

        log.info("Imported {} schemas from intelligence-service", schemasToImport.size());
        log.info("Imported {} paths from intelligence-service", importedPaths);
    }

    /**
     * Collect all schema names referenced in an operation's request/response bodies.
     */
    private void collectSchemasFromOperation(io.swagger.v3.oas.models.Operation operation, Set<String> out) {
        if (operation == null) return;

        // Collect from responses
        if (operation.getResponses() != null) {
            operation
                .getResponses()
                .forEach((code, response) -> {
                    if (response.getContent() != null) {
                        response
                            .getContent()
                            .forEach((type, media) -> {
                                if (media.getSchema() != null) {
                                    collectRefs(media.getSchema(), out);
                                }
                            });
                    }
                });
        }

        // Collect from request body
        if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
            operation
                .getRequestBody()
                .getContent()
                .forEach((type, media) -> {
                    if (media.getSchema() != null) {
                        collectRefs(media.getSchema(), out);
                    }
                });
        }
    }

    /**
     * Check if a schema or operation is tagged with x-hephaestus.export: true
     */
    private boolean isTaggedForExport(Object item) {
        Map<String, Object> extensions = null;

        if (item instanceof Schema<?> schema) {
            extensions = schema.getExtensions();
        } else if (item instanceof io.swagger.v3.oas.models.Operation op) {
            extensions = op.getExtensions();
        }

        if (extensions == null) {
            return false;
        }

        Object hephaestus = extensions.get("x-hephaestus");
        if (hephaestus instanceof Map<?, ?> map) {
            Object export = map.get("export");
            if (export instanceof Boolean b) return b;
            if (export instanceof String s) return Boolean.parseBoolean(s);
        }

        return false;
    }

    /**
     * Recursively collect all schemas referenced by a given schema.
     */
    private void collectReferencedSchemas(
        String name,
        Map<String, Schema<?>> allSchemas,
        Set<String> toImport,
        Set<String> visited
    ) {
        if (name == null || visited.contains(name)) return;
        visited.add(name);

        Schema<?> schema = allSchemas.get(name);
        if (schema == null) return;

        Set<String> refs = new HashSet<>();
        collectRefs(schema, refs);

        for (String ref : refs) {
            if (!toImport.contains(ref)) {
                toImport.add(ref);
                collectReferencedSchemas(ref, allSchemas, toImport, visited);
            }
        }
    }

    /**
     * Collect $ref names from a schema.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void collectRefs(Schema schema, Set<String> out) {
        if (schema == null) return;

        if (schema.get$ref() != null) {
            String ref = schema.get$ref();
            out.add(ref.substring(ref.lastIndexOf('/') + 1));
        }

        Map<String, Schema> props = schema.getProperties();
        if (props != null) {
            props.values().forEach(p -> collectRefs(p, out));
        }

        if (schema.getItems() != null) {
            collectRefs(schema.getItems(), out);
        }

        if (schema.getAllOf() != null) {
            schema.getAllOf().forEach(s -> collectRefs((Schema) s, out));
        }
        if (schema.getAnyOf() != null) {
            schema.getAnyOf().forEach(s -> collectRefs((Schema) s, out));
        }
        if (schema.getOneOf() != null) {
            schema.getOneOf().forEach(s -> collectRefs((Schema) s, out));
        }
    }

    /**
     * Process all paths: clean up tags, parameters, ensure workspaceSlug.
     */
    private void processAllPaths(OpenAPI openApi) {
        var paths = openApi.getPaths();
        if (paths == null) return;

        paths.forEach((path, pathItem) -> {
            pathItem
                .readOperations()
                .forEach(operation -> {
                    // Remove DTO suffix from response/request schemas
                    if (operation.getResponses() != null) {
                        operation
                            .getResponses()
                            .forEach((code, response) -> {
                                if (response.getContent() != null) {
                                    response
                                        .getContent()
                                        .forEach((type, media) -> {
                                            if (media.getSchema() != null) {
                                                removeDtoSuffixFromRefs(media.getSchema());
                                            }
                                        });
                                }
                            });
                    }

                    if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
                        operation
                            .getRequestBody()
                            .getContent()
                            .forEach((type, media) -> {
                                if (media.getSchema() != null) {
                                    removeDtoSuffixFromRefs(media.getSchema());
                                }
                            });
                    }

                    // Clean up controller suffix from tags
                    if (operation.getTags() != null) {
                        operation.setTags(
                            operation
                                .getTags()
                                .stream()
                                .map(tag -> tag.endsWith("-controller") ? tag.substring(0, tag.length() - 11) : tag)
                                .collect(Collectors.toList())
                        );
                    }

                    // Filter out WorkspaceContext parameter
                    if (operation.getParameters() != null) {
                        operation.setParameters(
                            operation
                                .getParameters()
                                .stream()
                                .filter(p -> !isWorkspaceContextParam(p))
                                .collect(Collectors.toCollection(ArrayList::new))
                        );
                    }

                    // Ensure workspaceSlug parameter for workspace paths
                    if (path.contains("{workspaceSlug}")) {
                        ensureWorkspaceSlugParam(operation);
                    }
                });
        });

        // Also normalize refs inside component schemas (not only request/response bodies)
        if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
            openApi.getComponents().getSchemas().values().forEach(this::removeDtoSuffixFromRefs);
        }
    }

    private boolean isWorkspaceContextParam(Parameter param) {
        if (param == null) return false;
        if ("workspaceContext".equals(param.getName())) return true;
        if (param.getSchema() != null && param.getSchema().get$ref() != null) {
            return param.getSchema().get$ref().endsWith("/WorkspaceContext");
        }
        return false;
    }

    private void ensureWorkspaceSlugParam(io.swagger.v3.oas.models.Operation operation) {
        var params = operation.getParameters();
        if (params == null) {
            params = new ArrayList<>();
            operation.setParameters(params);
        } else {
            // `Stream#toList()` returns an unmodifiable list; also external sources may provide immutable lists.
            // Ensure we can safely insert the workspaceSlug parameter.
            params = new ArrayList<>(params);
            operation.setParameters(params);
        }

        boolean exists = params.stream().anyMatch(p -> "workspaceSlug".equals(p.getName()) && "path".equals(p.getIn()));

        if (!exists) {
            params.add(
                0,
                new Parameter()
                    .name("workspaceSlug")
                    .in("path")
                    .required(true)
                    .description("Workspace slug")
                    .schema(new StringSchema().pattern("^[a-z0-9][a-z0-9-]{2,50}$"))
            );
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void removeDtoSuffixFromRefs(Schema schema) {
        if (schema == null) return;

        if (schema.get$ref() != null && schema.get$ref().endsWith("DTO")) {
            schema.set$ref(schema.get$ref().substring(0, schema.get$ref().length() - 3));
        }

        Map<String, Schema> props = schema.getProperties();
        if (props != null) {
            props.values().forEach(this::removeDtoSuffixFromRefs);
        }

        if (schema.getItems() != null) {
            removeDtoSuffixFromRefs(schema.getItems());
        }

        if (schema.getAllOf() != null) {
            schema.getAllOf().forEach(s -> removeDtoSuffixFromRefs((Schema) s));
        }
        if (schema.getAnyOf() != null) {
            schema.getAnyOf().forEach(s -> removeDtoSuffixFromRefs((Schema) s));
        }
        if (schema.getOneOf() != null) {
            schema.getOneOf().forEach(s -> removeDtoSuffixFromRefs((Schema) s));
        }

        Object additional = schema.getAdditionalProperties();
        if (additional instanceof Schema additionalSchema) {
            removeDtoSuffixFromRefs(additionalSchema);
        }

        if (schema.getNot() != null) {
            removeDtoSuffixFromRefs(schema.getNot());
        }
    }
}
