package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemDescriptor;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

class ScmItemDescriptorSchemaTest extends BaseUnitTest {
    @ParameterizedTest
    @MethodSource("descriptors")
    void shouldLoadTheShippedRecordSchemaForEveryScmKind(ItemDescriptor descriptor) throws IOException {
        var resource = new ClassPathResource(descriptor.recordSchemaResource());
        assertThat(resource.exists())
                .as("record schema for %s", descriptor.kind())
                .isTrue();
        try (var stream = resource.getInputStream()) {
            var schema = JsonMapper.builder().build().readTree(stream);
            assertThat(schema.path("$schema").asString()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
            assertThat(schema.path("type").asString()).isEqualTo("object");
            assertThat(schema.path("properties").isObject()).isTrue();
            assertThat(schema.path("required").isArray()).isTrue();
        }
    }

    private static Stream<ItemDescriptor> descriptors() {
        return Stream.of(
                new PullRequestItemDescriptor(),
                new PullRequestReviewItemDescriptor(),
                new IssueItemDescriptor(),
                new RepositoryItemDescriptor(),
                new FileItemDescriptor());
    }
}
