package de.tum.cit.aet.hephaestus.integration.core.itemevent;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

@Converter(autoApply = true)
public class ItemKindConverter implements AttributeConverter<ItemKind, String> {
    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable ItemKind kind) {
        return kind == null ? null : kind.value();
    }

    @Override
    public @Nullable ItemKind convertToEntityAttribute(@Nullable String value) {
        return value == null ? null : new ItemKind(value);
    }
}
