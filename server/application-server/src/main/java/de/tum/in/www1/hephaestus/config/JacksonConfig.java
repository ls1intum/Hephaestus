package de.tum.in.www1.hephaestus.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.TimeZone;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

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
     * Also configured to ignore unknown properties during deserialization.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        // Use Spring's builder so application.yml jackson.* properties are honored
        ObjectMapper mapper = builder.createXmlMapper(false).build();

        // Ensure JavaTime and JsonNullable support and desired defaults
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new JsonNullableModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Serialize java.time types as ISO-8601 strings (not timestamps)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Align with configured UTC timezone
        mapper.setTimeZone(TimeZone.getTimeZone("UTC"));

        return mapper;
    }
}
