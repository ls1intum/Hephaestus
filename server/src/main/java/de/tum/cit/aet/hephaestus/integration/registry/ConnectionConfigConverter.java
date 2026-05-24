package de.tum.cit.aet.hephaestus.integration.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JPA converter for the sealed {@link ConnectionConfig} JSONB column.
 *
 * <p>Delegates to Spring's auto-configured {@code ObjectMapper} so the
 * {@code @JsonTypeInfo} discriminator is respected. Hibernate's native JSON
 * binding (which uses its own ObjectMapper) is not used here because polymorphic
 * sealed-type round-trip is unproven on that path.
 */
@Component
@Converter(autoApply = false)
public class ConnectionConfigConverter implements AttributeConverter<ConnectionConfig, String> {

    private static ObjectMapper sharedMapper;

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        ConnectionConfigConverter.sharedMapper = objectMapper;
    }

    @Override
    public String convertToDatabaseColumn(ConnectionConfig attribute) {
        if (attribute == null) return null;
        try {
            return mapper().writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize ConnectionConfig", e);
        }
    }

    @Override
    public ConnectionConfig convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return mapper().readValue(dbData, ConnectionConfig.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize ConnectionConfig: " + dbData, e);
        }
    }

    private static ObjectMapper mapper() {
        if (sharedMapper == null) {
            // Bootstrap fallback for tests / detached usage. Discouraged in production.
            sharedMapper = new ObjectMapper();
        }
        return sharedMapper;
    }
}
