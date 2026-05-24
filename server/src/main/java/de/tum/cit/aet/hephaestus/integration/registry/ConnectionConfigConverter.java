package de.tum.cit.aet.hephaestus.integration.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JPA converter for the sealed {@link ConnectionConfig} JSONB column. Delegates to
 * Spring's auto-configured {@code ObjectMapper} so the {@code @JsonTypeInfo}
 * discriminator survives round-trip; Hibernate's native JSON binding is not used
 * because sealed-type support there is unproven.
 */
@Component
@Converter(autoApply = false)
public class ConnectionConfigConverter implements AttributeConverter<ConnectionConfig, String> {

    /** Static so Hibernate's no-arg materialisation still hits the wired mapper. */
    private static volatile ObjectMapper sharedMapper;

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
        ObjectMapper m = sharedMapper;
        if (m != null) {
            return m;
        }
        // Bootstrap fallback for unit tests / detached Hibernate. Register modules so
        // Java-time types (Instant in expiresAt-bearing configs) round-trip correctly
        // — the previous bare mapper silently broke as soon as a Java-time field landed.
        synchronized (ConnectionConfigConverter.class) {
            if (sharedMapper == null) {
                sharedMapper = new ObjectMapper().findAndRegisterModules();
            }
            return sharedMapper;
        }
    }
}
