package de.tum.in.www1.hephaestus.config.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldDateValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldIterationValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldLabelValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldMilestoneValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldNumberValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldPullRequestValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldRepositoryValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldReviewerValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldSingleSelectValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldTextValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldUserValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldValue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for Jackson mixin-based polymorphic deserialization of GitHub ProjectV2 item field values.
 */
@Tag("unit")
@DisplayName("GitHubProjectV2ItemFieldValueMixin")
class GitHubProjectV2ItemFieldValueMixinTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.USE_LONG_FOR_INTS, true);
        objectMapper.addMixIn(GHProjectV2ItemFieldValue.class, GitHubProjectV2ItemFieldValueMixin.class);
    }

    @Nested
    @DisplayName("Polymorphic deserialization by __typename")
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
        @DisplayName("should deserialize __typename to correct GH class")
        void shouldDeserializeToCorrectType(String typename, String expectedClassName) throws Exception {
            String json = "{\"__typename\": \"" + typename + "\"}";

            GHProjectV2ItemFieldValue result = objectMapper.readValue(json, GHProjectV2ItemFieldValue.class);

            assertThat(result).isNotNull();
            assertThat(result.getClass().getSimpleName()).isEqualTo(expectedClassName);
        }
    }

    @Nested
    @DisplayName("Fallback behavior")
    class FallbackBehavior {

        @Test
        @DisplayName("should fall back to GHProjectV2ItemFieldTextValue for unknown __typename")
        void shouldFallbackForUnknownTypename() throws Exception {
            String json = "{\"__typename\": \"ProjectV2ItemFieldUnknownFutureType\"}";

            GHProjectV2ItemFieldValue result = objectMapper.readValue(json, GHProjectV2ItemFieldValue.class);

            assertThat(result).isInstanceOf(GHProjectV2ItemFieldTextValue.class);
        }

        @Test
        @DisplayName("should fall back to GHProjectV2ItemFieldTextValue when __typename is missing")
        void shouldFallbackWhenTypenameIsMissing() throws Exception {
            String json = "{\"someField\": \"someValue\"}";

            GHProjectV2ItemFieldValue result = objectMapper.readValue(json, GHProjectV2ItemFieldValue.class);

            assertThat(result).isInstanceOf(GHProjectV2ItemFieldTextValue.class);
        }
    }

    @Nested
    @DisplayName("Array deserialization")
    class ArrayDeserialization {

        @Test
        @DisplayName("should deserialize mixed types in an array")
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
    @DisplayName("Specific type deserialization")
    class SpecificTypeDeserialization {

        @Test
        @DisplayName("should preserve text value fields")
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
        @DisplayName("should preserve number value fields")
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
        @DisplayName("should ignore unknown properties")
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
