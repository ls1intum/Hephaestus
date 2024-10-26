package de.tum.in.www1.hephaestus;

import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.media.Schema;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Hephaestus API", 
        description = "API documentation for the Hephaestus application server.", 
        version = "0.0.1",
        contact = @Contact(
            name = "Felix T.J. Dietrich",
            email = "felixtj.dietrich@tum.de"
        ), 
        license = @License(
            name = "MIT License",
            url = "https://github.com/ls1intum/Hephaestus/blob/develop/LICENSE"
        )
    ), 
    servers = {
        @Server(url = "/", description = "Default Server URL"),
    }
)
public class OpenAPIConfiguration {

    Logger logger = LoggerFactory.getLogger(OpenAPIConfiguration.class);

    @Bean
    public OpenApiCustomizer schemaCustomizer() {
        return openApi -> {
            var components = openApi.getComponents();

            // Only include schemas with DTO suffix and remove the suffix
            var schemas = components
                    .getSchemas()
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getKey().endsWith("DTO"))
                    .collect(Collectors.toMap(entry -> entry.getKey().substring(0, entry.getKey().length() - 3),
                            entry -> {
                                var schema = entry.getValue();
                                schema.setName(entry.getKey().substring(0, entry.getKey().length() - 3));
                                return schema;
                            }));

            // Remove DTO suffix from attribute names
            schemas.forEach((key, value) -> {
                Map<String, Schema<?>> properties = value.getProperties();
                if (properties != null) {
                    properties.forEach((propertyKey, propertyValue) -> {
                        removeDTOSuffixesFromSchemaRecursively(propertyValue);
                    });
                }
            });

            components.setSchemas(schemas);

            // Remove DTO suffix from reponse schemas
            var paths = openApi.getPaths();
            paths.forEach((path, pathItem) -> {
                logger.info(path);
                pathItem.readOperations().forEach(operation -> {
                    var responses = operation.getResponses();
                    responses.forEach((responseCode, response) -> {
                        var content = response.getContent();
                        content.forEach((contentType, mediaType) -> {
                            removeDTOSuffixesFromSchemaRecursively(mediaType.getSchema());

                        });
                    });
                });
            });
        };
    }

    private void removeDTOSuffixesFromSchemaRecursively(Schema<?> schema) {
        if (schema.get$ref() != null && schema.get$ref().endsWith("DTO")) {
            schema.set$ref(schema.get$ref().substring(0, schema.get$ref().length() - 3));
        }

        if (schema.getItems() != null) {
            removeDTOSuffixesFromSchemaRecursively(schema.getItems());
        }
    }
}