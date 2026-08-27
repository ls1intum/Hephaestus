package de.tum.cit.aet.hephaestus.integration.core.signal;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

/**
 * Maps an {@link ArtifactKind} onto the {@code artifact_kind} string column it is stored in.
 *
 * <p>Auto-applied, because the alternative is one {@code @Convert} per column and a new column that
 * silently persists {@code ArtifactKind.toString()} the day somebody forgets one.
 *
 * <p>Reading validates: a row whose value no longer parses fails here rather than flowing into the
 * application as a kind nothing declares.
 */
@Converter(autoApply = true)
public class ArtifactKindConverter implements AttributeConverter<ArtifactKind, String> {

    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable ArtifactKind attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public @Nullable ArtifactKind convertToEntityAttribute(@Nullable String dbData) {
        return dbData == null ? null : ArtifactKind.of(dbData);
    }
}
