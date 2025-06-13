package de.tum.in.www1.hephaestus.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Global Jackson configuration for the application.
 * Configures ObjectMapper to handle JsonNullable fields properly.
 */
@Configuration
public class JacksonConfig {

    /**
     * Primary ObjectMapper bean with JsonNullable module support.
     * This ensures that all JSON serialization/deserialization throughout the application
     * can handle JsonNullable fields from OpenAPI-generated classes.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JsonNullableModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
