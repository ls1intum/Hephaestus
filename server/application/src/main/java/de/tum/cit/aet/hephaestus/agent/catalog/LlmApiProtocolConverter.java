package de.tum.cit.aet.hephaestus.agent.catalog;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

/** Persists {@link LlmApiProtocol#wire()}, never constant names; unknown stored values must fail the read. */
@Converter(autoApply = true)
public class LlmApiProtocolConverter implements AttributeConverter<LlmApiProtocol, String> {

    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable LlmApiProtocol attribute) {
        return attribute == null ? null : attribute.wire();
    }

    @Override
    public @Nullable LlmApiProtocol convertToEntityAttribute(@Nullable String dbData) {
        return dbData == null
                ? null
                : LlmApiProtocol.parse(dbData)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown api_protocol: " + dbData));
    }
}
