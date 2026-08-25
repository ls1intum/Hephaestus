package de.tum.cit.aet.hephaestus.core.audit;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Keeps append-only audit rows readable after entity-type vocabulary changes. */
@Converter
public class ConfigAuditEntityTypeConverter implements AttributeConverter<ConfigAuditEntityType, String> {

    @Override
    public String convertToDatabaseColumn(ConfigAuditEntityType value) {
        return value.name();
    }

    @Override
    public ConfigAuditEntityType convertToEntityAttribute(String value) {
        return switch (value) {
            case "PRACTICE_AREA" -> ConfigAuditEntityType.PRACTICE_GROUP;
            case "CURATED_PRACTICE_AREA" -> ConfigAuditEntityType.CURATED_PRACTICE_GROUP;
            default -> ConfigAuditEntityType.valueOf(value);
        };
    }
}
