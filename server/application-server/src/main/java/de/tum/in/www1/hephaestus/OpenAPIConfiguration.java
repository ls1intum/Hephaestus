package de.tum.in.www1.hephaestus;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Hephaestus API",
        description = "API documentation for the Hephaestus application server.",
        version = "0.9.0-rc.27",
        contact = @Contact(name = "Felix T.J. Dietrich", email = "felixtj.dietrich@tum.de"),
        license = @License(name = "MIT License", url = "https://github.com/ls1intum/Hephaestus/blob/develop/LICENSE")
    ),
    servers = { @Server(url = "/", description = "Default Server URL") }
)
public class OpenAPIConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(OpenAPIConfiguration.class);

    // Intentionally left without domain object allowlist; we retain all non-DTO schemas.

    // Optional absolute or relative path to the stored mentor OpenAPI YAML
    // Example (absolute): /Users/you/Hephaestus/server/intelligence-service/openapi.yaml
    // Example (relative to application-server module): ../intelligence-service/openapi.yaml
    @Value("${hephaestus.intelligence-service.openapi.path:}")
    private String mentorOpenapiPath;

    @Bean
    public OpenApiCustomizer schemaCustomizer() {
        return openApi -> {
            // 1) Merge mentor paths and components from the stored intelligence-service OpenAPI file
            mergeMentorOpenAPIFromFile(openApi);

            var components = openApi.getComponents();

            if (components != null && components.getSchemas() != null) {
                // 2) Rename DTO schemas (without dropping non-DTO schemas such as those from mentor service)
                @SuppressWarnings("rawtypes")
                Map<String, Schema> schemas = new LinkedHashMap<>(components.getSchemas());

                // Collect keys to rename first to avoid concurrent modification
                var keysToRename = schemas.keySet().stream().filter(k -> k.endsWith("DTO")).toList();
                for (String oldKey : keysToRename) {
                    String newKey = oldKey.substring(0, oldKey.length() - 3);
                    Schema<?> schema = schemas.get(oldKey);
                    if (schema != null) {
                        schema.setName(newKey);
                        // If conflict, prefer existing non-DTO name
                        schemas.putIfAbsent(newKey, schema);
                        schemas.remove(oldKey);
                    }
                }

                // Ensure allowed domain objects remain (no-op here as we didn't drop anything)

                // Remove DTO suffixes inside properties and nested items
                schemas.forEach((k, v) -> {
                    @SuppressWarnings({ "rawtypes", "unchecked" })
                    Map<String, Schema> properties = v.getProperties();
                    if (properties != null) {
                        properties.forEach((propertyKey, propertyValue) -> removeDTOSuffixesFromSchemaRecursively(propertyValue));
                    }
                });

                components.setSchemas(schemas);
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

                            // Remove '-controller' suffix from tags only if present
                            if (operation.getTags() != null) {
                                var updated = operation
                                    .getTags()
                                    .stream()
                                    .map(tag -> tag.endsWith("-controller") ? tag.substring(0, tag.length() - 11) : tag)
                                    .collect(Collectors.toList());
                                operation.setTags(updated);
                            }
                        });
                });
            } else {
                logger.warn("Paths are null in OpenAPI configuration.");
            }
        };
    }

    private void mergeMentorOpenAPIFromFile(OpenAPI target) {
        try {
            String specBody = readMentorSpecFromFile();
            if (specBody == null || specBody.isBlank()) {
                logger.warn("Mentor OpenAPI file not found or empty. Skipping mentor spec merge.");
                return;
            }

            SwaggerParseResult result = new OpenAPIV3Parser().readContents(specBody, null, null);
            if (result == null || result.getOpenAPI() == null) {
                logger.warn("Failed to parse mentor OpenAPI from file: {}", result != null ? result.getMessages() : "no result");
                return;
            }

            OpenAPI remote = result.getOpenAPI();

            // Ensure paths/components are initialized on target
            if (target.getPaths() == null) {
                target.setPaths(new Paths());
            }
            if (target.getComponents() == null) {
                target.setComponents(new Components());
            }

            // Copy only mentor paths (paths starting with '/mentor') as-is
            if (remote.getPaths() != null) {
                remote
                    .getPaths()
                    .entrySet()
                    .stream()
                    .filter(e -> e.getKey() != null && e.getKey().startsWith("/mentor"))
                    .forEach(e -> {
                        // If the path already exists locally, don't overwrite
                        target.getPaths().addPathItem(e.getKey(), target.getPaths().getOrDefault(e.getKey(), e.getValue()));
                    });
            }

            // Merge referenced components defensively (do not overwrite existing ones)
            if (remote.getComponents() != null) {
                Components src = remote.getComponents();
                Components dst = target.getComponents();

                if (src.getSchemas() != null) {
                    if (dst.getSchemas() == null) dst.setSchemas(new HashMap<>());
                    src.getSchemas().forEach((k, v) -> dst.getSchemas().putIfAbsent(k, v));
                }
                if (src.getParameters() != null) {
                    if (dst.getParameters() == null) dst.setParameters(new HashMap<>());
                    src.getParameters().forEach((k, v) -> dst.getParameters().putIfAbsent(k, v));
                }
                if (src.getResponses() != null) {
                    if (dst.getResponses() == null) dst.setResponses(new HashMap<>());
                    src.getResponses().forEach((k, v) -> dst.getResponses().putIfAbsent(k, v));
                }
                if (src.getRequestBodies() != null) {
                    if (dst.getRequestBodies() == null) dst.setRequestBodies(new HashMap<>());
                    src.getRequestBodies().forEach((k, v) -> dst.getRequestBodies().putIfAbsent(k, v));
                }
                if (src.getHeaders() != null) {
                    if (dst.getHeaders() == null) dst.setHeaders(new HashMap<>());
                    src.getHeaders().forEach((k, v) -> dst.getHeaders().putIfAbsent(k, v));
                }
                if (src.getSecuritySchemes() != null) {
                    if (dst.getSecuritySchemes() == null) dst.setSecuritySchemes(new HashMap<>());
                    src.getSecuritySchemes().forEach((k, v) -> dst.getSecuritySchemes().putIfAbsent(k, v));
                }
            }

            logger.info("Merged mentor OpenAPI from local file");
        } catch (Exception ex) {
            logger.warn("Could not merge mentor OpenAPI from file: {}", ex.getMessage());
        }
    }

    private String readMentorSpecFromFile() {
        // 1) If explicit path is provided via properties, try it first
        if (mentorOpenapiPath != null && !mentorOpenapiPath.isBlank()) {
            try {
                Path p = java.nio.file.Paths.get(mentorOpenapiPath);
                if (Files.exists(p)) {
                    return Files.readString(p, StandardCharsets.UTF_8);
                } else {
                    logger.warn("Configured mentor OpenAPI path does not exist: {}", mentorOpenapiPath);
                }
            } catch (Exception e) {
                logger.warn("Failed reading mentor OpenAPI from configured path {}: {}", mentorOpenapiPath, e.getMessage());
            }
        }

        // 2) Try sibling module default: ../intelligence-service/openapi.yaml (relative to application-server module)
        try {
            Path cwd = java.nio.file.Paths.get(System.getProperty("user.dir")).toAbsolutePath();
            Path sibling = cwd.resolveSibling("intelligence-service").resolve("openapi.yaml");
            if (Files.exists(sibling)) {
                return Files.readString(sibling, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            logger.debug("Failed reading mentor OpenAPI from sibling path: {}", e.getMessage());
        }

        // 3) Try repo layout: <repo-root>/server/intelligence-service/openapi.yaml
        try {
            Path cwd = java.nio.file.Paths.get(System.getProperty("user.dir")).toAbsolutePath();
            // If cwd is .../server/application-server, repoRoot is cwd.getParent().getParent()
            Path repoRoot = cwd.getParent() != null ? cwd.getParent().getParent() : null;
            if (repoRoot != null) {
                Path inRepo = repoRoot.resolve("server").resolve("intelligence-service").resolve("openapi.yaml");
                if (Files.exists(inRepo)) {
                    return Files.readString(inRepo, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            logger.debug("Failed reading mentor OpenAPI from repository path: {}", e.getMessage());
        }

        return null;
    }

    private void removeDTOSuffixesFromSchemaRecursively(Schema<?> schema) {
        if (schema == null) {
            return;
        }

        if (schema.get$ref() != null && schema.get$ref().endsWith("DTO")) {
            String originalRef = schema.get$ref();
            // Remove DTO suffix for regular DTOs
            String newRef = originalRef.substring(0, originalRef.length() - 3);
            schema.set$ref(newRef);
            logger.debug("Updated $ref from {} to {}", originalRef, newRef);
        }

        if (schema.getItems() != null) {
            removeDTOSuffixesFromSchemaRecursively(schema.getItems());
        }
    }
}
