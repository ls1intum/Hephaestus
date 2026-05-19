package de.tum.cit.aet.hephaestus;

import de.tum.cit.aet.hephaestus.achievement.AchievementRegistry;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI configuration: processes application-server DTOs (strips the {@code DTO}
 * suffix from schema names and {@code $ref}s) and normalises paths (workspace-slug
 * parameter, tag cleanup, WorkspaceContext filtering).
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Hephaestus API",
        description = "API documentation for the Hephaestus application server.",
        version = "0.0.0-development",
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

    /**
     * Domain objects to include even without DTO suffix
     */
    private static final List<String> ALLOWED_DOMAIN_OBJECTS = List.of("PageableObject", "SortObject");
    /**
     * Domain objects to include by specific suffix (like AchievementProgress records)
     */
    private static final List<String> SAFE_DOMAIN_SUFFIXES = List.of("AchievementProgress");

    @Bean
    public OpenApiCustomizer schemaCustomizer(
        AchievementRegistry registry,
        @Value("${spring.application.version:0.0.0-development}") String appVersion
    ) {
        return openApi -> {
            openApi.getInfo().setVersion(appVersion);
            processApplicationServerSchemas(openApi);
            processAllPaths(openApi);

            // Inject AchievementId enum based on registry keys
            if (openApi.getComponents() != null) {
                // Collect and sort IDs for deterministic output
                List<String> achievementIds = new ArrayList<>(registry.getAchievementIds());
                Collections.sort(achievementIds);

                log.info("Injected {} achievement IDs into OpenAPI", achievementIds.size());
                if (achievementIds.isEmpty()) {
                    log.error(
                        "Achievement registry is empty during OpenAPI generation! This will cause frontend type errors."
                    );
                }

                StringSchema idSchema = new StringSchema();
                idSchema.setEnum(achievementIds);
                openApi.getComponents().addSchemas("AchievementId", idSchema);
            }
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
            .filter(
                e ->
                    ALLOWED_DOMAIN_OBJECTS.contains(e.getKey()) ||
                    SAFE_DOMAIN_SUFFIXES.stream().anyMatch(s -> e.getKey().endsWith(s))
            )
            .forEach(e -> filteredSchemas.put(e.getKey(), e.getValue()));

        // Update $ref to remove DTO suffix
        filteredSchemas.values().forEach(this::removeDtoSuffixFromRefs);

        components.setSchemas(filteredSchemas);
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
