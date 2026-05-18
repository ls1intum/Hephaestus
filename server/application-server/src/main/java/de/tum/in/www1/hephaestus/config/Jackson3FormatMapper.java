package de.tum.in.www1.hephaestus.config;

import java.lang.reflect.Type;
import org.hibernate.HibernateException;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Jackson 3 implementation of Hibernate's JSON {@link FormatMapper} SPI. */
public final class Jackson3FormatMapper implements FormatMapper {

    private final JsonMapper objectMapper;

    public Jackson3FormatMapper(JsonMapper objectMapper) {
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
