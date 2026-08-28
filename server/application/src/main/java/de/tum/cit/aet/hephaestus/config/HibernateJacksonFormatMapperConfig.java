package de.tum.cit.aet.hephaestus.config;

import java.util.Map;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class HibernateJacksonFormatMapperConfig implements HibernatePropertiesCustomizer {

    private final ObjectMapper objectMapper;

    public HibernateJacksonFormatMapperConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put("hibernate.type.json_format_mapper", new Jackson3FormatMapper(objectMapper));
    }
}
