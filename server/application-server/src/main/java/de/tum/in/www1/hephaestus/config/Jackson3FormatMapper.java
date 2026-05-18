package de.tum.in.www1.hephaestus.config;

import java.lang.reflect.Type;
import org.hibernate.HibernateException;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Hibernate {@link FormatMapper} backed by Jackson 3. Bridges JSONB columns typed as
 * {@code tools.jackson.databind.JsonNode} until Hibernate 7.3 ships its own Jackson 3 mapper
 * (HHH-19890) — at that point this class is deletable in favour of
 * {@code org.hibernate.type.format.jackson.Jackson3JsonFormatMapper}.
 */
public final class Jackson3FormatMapper implements FormatMapper {

    private final ObjectMapper objectMapper;

    public Jackson3FormatMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> T fromString(CharSequence charSequence, JavaType<T> javaType, WrapperOptions wrapperOptions) {
        if (charSequence == null) {
            return null;
        }
        try {
            Type rawType = javaType.getJavaType();
            return objectMapper.readValue(charSequence.toString(), objectMapper.constructType(rawType));
        } catch (JacksonException e) {
            throw new HibernateException(
                "Could not deserialize JSON to " + javaType.getJavaTypeClass().getName() + ": " + e.getMessage(),
                e
            );
        }
    }

    @Override
    public <T> String toString(T value, JavaType<T> javaType, WrapperOptions wrapperOptions) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new HibernateException(
                "Could not serialize " + javaType.getJavaTypeClass().getName() + " to JSON: " + e.getMessage(),
                e
            );
        }
    }
}
