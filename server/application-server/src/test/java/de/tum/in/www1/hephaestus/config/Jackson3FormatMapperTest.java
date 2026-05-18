package de.tum.in.www1.hephaestus.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.hibernate.HibernateException;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.spi.JavaTypeRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class Jackson3FormatMapperTest extends BaseUnitTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Jackson3FormatMapper mapper = new Jackson3FormatMapper(objectMapper);

    @Test
    void malformedJsonWrapsException() {
        assertThatThrownBy(() -> mapper.fromString("{not json", javaTypeOf(JsonNode.class), null))
            .isInstanceOf(HibernateException.class)
            .hasRootCauseInstanceOf(JacksonException.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> JavaType<T> javaTypeOf(Class<T> type) {
        return (JavaType<T>) new JavaTypeRegistry(null).getDescriptor(type);
    }
}
