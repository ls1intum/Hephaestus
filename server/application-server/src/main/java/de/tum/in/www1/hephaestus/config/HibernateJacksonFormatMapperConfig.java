package de.tum.in.www1.hephaestus.config;

import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Plugs {@link Jackson3FormatMapper} into Hibernate as the JSON {@code FormatMapper}.
 *
 * <p>Hibernate 7's default Jackson mapper (Jackson 2) cannot serialize/deserialize Jackson 3
 * types like {@code tools.jackson.databind.JsonNode}. Without this customizer, every
 * {@code @JdbcTypeCode(SqlTypes.JSON)} column with a Jackson 3 type fails at read/write with
 * {@code Cannot construct instance of tools.jackson.databind.JsonNode}.
 *
 * <p>Lives in the {@code config} package because it touches the persistence-layer wiring rather
 * than the JSON columns themselves.
 */
@Configuration
public class HibernateJacksonFormatMapperConfig implements HibernatePropertiesCustomizer {

    private final ObjectMapper objectMapper;

    public HibernateJacksonFormatMapperConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void customize(java.util.Map<String, Object> hibernateProperties) {
        // Hibernate accepts a FormatMapper instance directly under this property key.
        hibernateProperties.put("hibernate.type.json_format_mapper", new Jackson3FormatMapper(objectMapper));
    }
}
