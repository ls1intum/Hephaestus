package de.tum.in.www1.hephaestus.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Global Jackson configuration for the application.
 * Configures ObjectMapper to handle JsonNullable fields properly and Java 8 date/time types.
 */
@Configuration
public class JacksonConfig {

    /**
     * Primary ObjectMapper bean with JsonNullable module support and Java 8 date/time support.
     * This ensures that all JSON serialization/deserialization throughout the application
     * can handle JsonNullable fields from OpenAPI-generated classes and Java 8 date/time types like OffsetDateTime.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JsonNullableModule())
            .registerModule(new JavaTimeModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
