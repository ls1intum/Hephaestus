package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The published policy schema still describes the record it is published for.
 *
 * <p>Nothing in the server reads this schema — it is the contract an authoring tool, a reviewer, or a
 * future version of us reads instead of the Java. That is exactly why it rots: the record gained a
 * field for why a person is needed and lost the sources a review reads to the bindings, and the schema
 * went on describing the shape from two refactors ago. A published contract with no enforcement is
 * documentation, and documentation is where this drift went unnoticed the first time.
 *
 * <p>Only the shape is pinned here. Whether the schema <em>enforces</em> what it declares is exercised
 * by {@code scripts/validate-artifact-source-contracts.mjs}, which has a JSON Schema validator; this
 * has a compiler and a record, so it checks what only a compiler and a record can see.
 */
class PracticeAutomatedReviewPolicySchemaTest extends BaseUnitTest {

    private static final String SCHEMA_RESOURCE =
        "contracts/artifact-source/1.0.0/practice-automated-review-policy.schema.json";

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void shouldDeclareExactlyThePolicyRecordsFields() throws IOException {
        Set<String> declared = Set.copyOf(objectMapper.readTree(read()).path("properties").propertyNames());

        assertThat(declared).isEqualTo(componentNames());
    }

    /**
     * A component the record cannot be built without is a property the schema must require. Anything
     * nullable is optional, and the schema says when it may appear.
     */
    @Test
    void shouldRequireEveryFieldThePolicyCannotBeBuiltWithout() throws IOException {
        JsonNode required = objectMapper.readTree(read()).path("required");
        Set<String> requiredNames = required
            .valueStream()
            .map(JsonNode::asString)
            .collect(Collectors.toUnmodifiableSet());

        // JSpecify's @Nullable is a TYPE_USE annotation, so it is on the component's annotated type
        // rather than on the component declaration.
        Set<String> nonNullable = Arrays.stream(PracticeAutomatedReviewPolicy.class.getRecordComponents())
            .filter(component -> component.getAnnotatedType().getAnnotation(Nullable.class) == null)
            .map(RecordComponent::getName)
            .collect(Collectors.toUnmodifiableSet());

        assertThat(requiredNames).isEqualTo(nonNullable);
    }

    private static Set<String> componentNames() {
        return Arrays.stream(PracticeAutomatedReviewPolicy.class.getRecordComponents())
            .map(RecordComponent::getName)
            .collect(Collectors.toUnmodifiableSet());
    }

    private InputStream read() throws IOException {
        return new ClassPathResource(SCHEMA_RESOURCE).getInputStream();
    }
}
