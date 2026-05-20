package de.tum.cit.aet.hephaestus.config;

import java.lang.reflect.Type;
import org.hibernate.HibernateException;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

// TODO: replace with org.hibernate.type.format.jackson.Jackson3JsonFormatMapper once on Hibernate 7.3+ (HHH-19890).
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
                "JSON deserialize failed for " + javaType.getJavaTypeClass().getSimpleName(),
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
            throw new HibernateException("JSON serialize failed for " + javaType.getJavaTypeClass().getSimpleName(), e);
        }
    }
}
