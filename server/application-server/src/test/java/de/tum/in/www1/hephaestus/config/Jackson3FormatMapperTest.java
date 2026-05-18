package de.tum.in.www1.hephaestus.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.hibernate.HibernateException;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.spi.JavaTypeRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Contract for the Jackson 3 ↔ Hibernate JSON {@link Jackson3FormatMapper} bridge. */
@DisplayName("Jackson3FormatMapper")
class Jackson3FormatMapperTest extends BaseUnitTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final Jackson3FormatMapper mapper = new Jackson3FormatMapper(objectMapper);

    @Test
    @DisplayName("round-trips a JsonNode tree")
    void roundTripJsonNode() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("title", "hello");
        node.put("count", 42);

        String serialized = mapper.toString(node, javaTypeOf(JsonNode.class), null);
        JsonNode deserialized = mapper.fromString(serialized, javaTypeOf(JsonNode.class), null);

        assertThat(deserialized).isEqualTo(node);
    }

    @Test
    @DisplayName("null input → null output (no NPE, no '\"null\"' literal)")
    void nullShortCircuits() {
        assertThat(mapper.toString(null, javaTypeOf(JsonNode.class), null)).isNull();
        assertThat((Object) mapper.fromString(null, javaTypeOf(JsonNode.class), null)).isNull();
    }

    @Test
    @DisplayName("malformed JSON throws HibernateException wrapping a JacksonException cause")
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
