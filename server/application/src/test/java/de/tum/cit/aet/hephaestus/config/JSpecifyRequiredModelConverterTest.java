package de.tum.cit.aet.hephaestus.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Behaviour lock for {@link JSpecifyRequiredModelConverter}.
 *
 * <p>swagger-core does not read JSpecify TYPE_USE {@code @NonNull}, so without this converter every
 * {@code required:} block vanishes from the generated spec. There is no CI drift check on
 * {@code openapi.yaml}, so this test is the regression guard: it pins exactly which members become
 * {@code required} for the shapes the codebase relies on (records, nested records, collections,
 * primitives, plain-class inheritance).
 */
@Tag("unit")
class JSpecifyRequiredModelConverterTest {

    // Swagger ModelConverters exposes raw Schema values in its public return type.
    @SuppressWarnings("rawtypes")
    private Map<String, Schema> resolve(Class<?> type) {
        ModelConverters converters = new ModelConverters();
        converters.addConverter(new JSpecifyRequiredModelConverter());
        return converters.readAll(new AnnotatedType(type));
    }

    @Test
    void marksExplicitlyNonNullMembersRequiredAndKeepsSchemaRequiredPrimitives() {
        Schema<?> schema = Objects.requireNonNull(resolve(Sample.class).get("Sample"));

        // Required iff the member is explicitly @NonNull, or declares @Schema(requiredMode = REQUIRED)
        // as a primitive does (mirrors the real GitLabPreflightResponseDTO#valid). @Nullable,
        // unannotated, and *bare* primitive members stay optional.
        assertThat(schema.getRequired()).containsExactlyInAnyOrder("nonNull", "requiredPrimitive", "items", "nested");
    }

    @Test
    void resolvesRequiredForNestedRecordReachedAsAJavaType() {
        // Nested types arrive at the converter as a Jackson JavaType, not a Class; this locks that path.
        Schema<?> nested = Objects.requireNonNull(resolve(Sample.class).get("Nested"));

        assertThat(nested.getRequired()).containsExactly("x");
    }

    @Test
    void walksSuperclassFieldsForPlainClasses() {
        Schema<?> schema = Objects.requireNonNull(resolve(Child.class).get("Child"));

        assertThat(schema.getRequired()).containsExactlyInAnyOrder("parentNonNull", "childNonNull");
    }

    @Test
    void shouldPublishEveryAlwaysPresentResponseKeyAndItsNullableType() {
        Schema<?> schema =
                Objects.requireNonNull(resolve(CompleteResponse.class).get("CompleteResponse"));
        assertThat(schema.getRequired()).containsExactlyInAnyOrder("reason", "count", "detail");
        Schema<?> reason = Objects.requireNonNull(schema.getProperties().get("reason"));
        Schema<?> detail = Objects.requireNonNull(schema.getProperties().get("detail"));
        assertThat(reason.getTypes()).containsExactlyInAnyOrder("string", "null");
        assertThat(detail.getAnyOf())
                .anySatisfy(member -> assertThat(member.getType()).isEqualTo("null"));
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    record CompleteResponse(
            @Nullable String reason, int count, @Nullable Nested detail) {}

    record Sample(
            @NonNull String nonNull,
            @Nullable String nullable,
            String bare,

            @io.swagger.v3.oas.annotations.media.Schema(requiredMode = RequiredMode.REQUIRED)
            int requiredPrimitive,

            int barePrimitive,
            @NonNull List<String> items,
            @NonNull Nested nested) {}

    record Nested(@NonNull String x, @Nullable String y) {}

    public static class Parent {

        @NonNull
        public String parentNonNull = "";

        @Nullable
        public String parentNullable;
    }

    public static class Child extends Parent {

        @NonNull
        public String childNonNull = "";

        @Nullable
        public String childNullable;
    }
}
