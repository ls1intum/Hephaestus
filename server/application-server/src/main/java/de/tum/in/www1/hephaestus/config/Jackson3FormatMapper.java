package de.tum.in.www1.hephaestus.config;

import java.lang.reflect.Type;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Hibernate {@link FormatMapper} backed by Jackson 3 ({@code tools.jackson.*}).
 *
 * <p>Hibernate 7.2 ships {@code org.hibernate.type.format.jackson.JacksonJsonFormatMapper}, which
 * is hard-wired to Jackson 2 ({@code com.fasterxml.jackson.databind.ObjectMapper}). With Spring Boot
 * 4 + Jackson 3 + our JSONB columns typed as {@code tools.jackson.databind.JsonNode}, Hibernate's
 * built-in mapper can't construct a Jackson 3 {@code JsonNode} and every JSONB read/write fails
 * with {@code Cannot construct instance of tools.jackson.databind.JsonNode (no Creators ...)}.
 *
 * <p>This mapper bridges Jackson 3 into Hibernate's {@link FormatMapper} SPI so {@code @JdbcTypeCode(SqlTypes.JSON)}
 * columns round-trip without keeping a duplicate Jackson 2 dependency on the classpath. Registered as
 * the Hibernate {@code hibernate.type.json_format_mapper} via {@link HibernateJacksonFormatMapperConfig}.
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
            tools.jackson.databind.JavaType jacksonType = objectMapper.constructType(rawType);
            return objectMapper.readValue(charSequence.toString(), jacksonType);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(
                "Could not deserialize JSON to " + javaType.getJavaTypeClass().getName() + ": " + e.getMessage(),
                e
            );
        }
    }

    @Override
    public <T> String toString(T value, JavaType<T> javaType, WrapperOptions wrapperOptions) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(
                "Could not serialize " + javaType.getJavaTypeClass().getName() + " to JSON: " + e.getMessage(),
                e
            );
        }
    }
}
