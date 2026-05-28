package de.tum.cit.aet.hephaestus.integration.scm.github.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.scm.github.graphql.model.GHProjectV2ItemFieldDateValue;
import de.tum.cit.aet.hephaestus.integration.scm.github.graphql.model.GHProjectV2ItemFieldIterationValue;
import de.tum.cit.aet.hephaestus.integration.scm.github.graphql.model.GHProjectV2ItemFieldLabelValue;
import de.tum.cit.aet.hephaestus.integration.scm.github.graphql.model.GHProjectV2ItemFieldMilestoneValue;
import de.tum.cit.aet.hephaestus.integration.scm.github.graphql.model.GHProjectV2ItemFieldNumberValue;
import de.tum.cit.aet.hephaestus.integration.scm.github.graphql.model.GHProjectV2ItemFieldPullRequestValue;
import de.tum.cit.aet.hephaestus.integration.scm.github.graphql.model.GHProjectV2ItemFieldRepositoryValue;
import de.tum.cit.aet.hephaestus.integration.scm.github.graphql.model.GHProjectV2ItemFieldReviewerValue;
import de.tum.cit.aet.hephaestus.integration.scm.github.graphql.model.GHProjectV2ItemFieldSingleSelectValue;
import de.tum.cit.aet.hephaestus.integration.scm.github.graphql.model.GHProjectV2ItemFieldTextValue;
import de.tum.cit.aet.hephaestus.integration.scm.github.graphql.model.GHProjectV2ItemFieldUserValue;
import de.tum.cit.aet.hephaestus.integration.scm.github.graphql.model.GHProjectV2ItemFieldValue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests for Jackson mixin-based polymorphic deserialization of GitHub ProjectV2 item field values.
 */
@Tag("unit")
class GitHubProjectV2ItemFieldValueMixinTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // GitHub responses omit numeric fields like iteration.duration; allow nulls for primitives.
        objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.USE_LONG_FOR_INTS)
            .addMixIn(GHProjectV2ItemFieldValue.class, GitHubProjectV2ItemFieldValueMixin.class)
            .build();
    }

    @Nested
    class PolymorphicDeserialization {

        @ParameterizedTest(name = "__typename={0} -> {1}")
        @CsvSource(
            {
                "ProjectV2ItemFieldTextValue, GHProjectV2ItemFieldTextValue",
                "ProjectV2ItemFieldNumberValue, GHProjectV2ItemFieldNumberValue",
                "ProjectV2ItemFieldDateValue, GHProjectV2ItemFieldDateValue",
                "ProjectV2ItemFieldSingleSelectValue, GHProjectV2ItemFieldSingleSelectValue",
                "ProjectV2ItemFieldIterationValue, GHProjectV2ItemFieldIterationValue",
                "ProjectV2ItemFieldLabelValue, GHProjectV2ItemFieldLabelValue",
                "ProjectV2ItemFieldMilestoneValue, GHProjectV2ItemFieldMilestoneValue",
                "ProjectV2ItemFieldPullRequestValue, GHProjectV2ItemFieldPullRequestValue",
                "ProjectV2ItemFieldRepositoryValue, GHProjectV2ItemFieldRepositoryValue",
                "ProjectV2ItemFieldReviewerValue, GHProjectV2ItemFieldReviewerValue",
                "ProjectV2ItemFieldUserValue, GHProjectV2ItemFieldUserValue",
            }
        )
        void shouldDeserializeToCorrectType(String typename, String expectedClassName) throws Exception {
            String json = "{\"__typename\": \"" + typename + "\"}";

            GHProjectV2ItemFieldValue result = objectMapper.readValue(json, GHProjectV2ItemFieldValue.class);

            assertThat(result).isNotNull();
            assertThat(result.getClass().getSimpleName()).isEqualTo(expectedClassName);
        }
    }

    @Nested
    class FallbackBehavior {

        @Test
        void shouldFallbackForUnknownTypename() throws Exception {
            String json = "{\"__typename\": \"ProjectV2ItemFieldUnknownFutureType\"}";

            GHProjectV2ItemFieldValue result = objectMapper.readValue(json, GHProjectV2ItemFieldValue.class);

            assertThat(result).isInstanceOf(GHProjectV2ItemFieldTextValue.class);
        }

        @Test
        void shouldFallbackWhenTypenameIsMissing() throws Exception {
            String json = "{\"someField\": \"someValue\"}";

            GHProjectV2ItemFieldValue result = objectMapper.readValue(json, GHProjectV2ItemFieldValue.class);

            assertThat(result).isInstanceOf(GHProjectV2ItemFieldTextValue.class);
        }
    }

    @Nested
    class ArrayDeserialization {

        @Test
        void shouldDeserializeMixedTypesInArray() throws Exception {
            String json = """
                [
                    {"__typename": "ProjectV2ItemFieldTextValue"},
                    {"__typename": "ProjectV2ItemFieldNumberValue"},
                    {"__typename": "ProjectV2ItemFieldDateValue"},
                    {"__typename": "ProjectV2ItemFieldSingleSelectValue"}
                ]
                """;

            List<GHProjectV2ItemFieldValue> results = objectMapper.readValue(
                json,
                new TypeReference<List<GHProjectV2ItemFieldValue>>() {}
            );

            assertThat(results).hasSize(4);
            assertThat(results.get(0)).isInstanceOf(GHProjectV2ItemFieldTextValue.class);
            assertThat(results.get(1)).isInstanceOf(GHProjectV2ItemFieldNumberValue.class);
            assertThat(results.get(2)).isInstanceOf(GHProjectV2ItemFieldDateValue.class);
            assertThat(results.get(3)).isInstanceOf(GHProjectV2ItemFieldSingleSelectValue.class);
        }
    }

    @Nested
    class SpecificTypeDeserialization {

        @Test
        void shouldPreserveTextValueFields() throws Exception {
            String json = """
                {"__typename": "ProjectV2ItemFieldTextValue", "text": "Hello World"}
                """;

            GHProjectV2ItemFieldValue result = objectMapper.readValue(json, GHProjectV2ItemFieldValue.class);

            assertThat(result).isInstanceOf(GHProjectV2ItemFieldTextValue.class);
            GHProjectV2ItemFieldTextValue textValue = (GHProjectV2ItemFieldTextValue) result;
            assertThat(textValue.getText()).isEqualTo("Hello World");
        }

        @Test
        void shouldPreserveNumberValueFields() throws Exception {
            String json = """
                {"__typename": "ProjectV2ItemFieldNumberValue", "number": 42.5}
                """;

            GHProjectV2ItemFieldValue result = objectMapper.readValue(json, GHProjectV2ItemFieldValue.class);

            assertThat(result).isInstanceOf(GHProjectV2ItemFieldNumberValue.class);
            GHProjectV2ItemFieldNumberValue numberValue = (GHProjectV2ItemFieldNumberValue) result;
            assertThat(numberValue.getNumber()).isEqualTo(42.5);
        }

        @Test
        void shouldIgnoreUnknownProperties() throws Exception {
            String json = """
                {"__typename": "ProjectV2ItemFieldTextValue", "text": "value", "unknownField": "ignored"}
                """;

            GHProjectV2ItemFieldValue result = objectMapper.readValue(json, GHProjectV2ItemFieldValue.class);

            assertThat(result).isInstanceOf(GHProjectV2ItemFieldTextValue.class);
            GHProjectV2ItemFieldTextValue textValue = (GHProjectV2ItemFieldTextValue) result;
            assertThat(textValue.getText()).isEqualTo("value");
        }
    }
}
