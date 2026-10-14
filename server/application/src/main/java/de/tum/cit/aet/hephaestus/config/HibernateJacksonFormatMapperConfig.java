package de.tum.cit.aet.hephaestus.config;

import java.util.Map;
import org.hibernate.type.format.jackson.Jackson3JsonFormatMapper;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class HibernateJacksonFormatMapperConfig implements HibernatePropertiesCustomizer {

    private final JsonMapper jsonMapper;

    public HibernateJacksonFormatMapperConfig(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put("hibernate.type.json_format_mapper", new Jackson3JsonFormatMapper(jsonMapper));
    }
}
