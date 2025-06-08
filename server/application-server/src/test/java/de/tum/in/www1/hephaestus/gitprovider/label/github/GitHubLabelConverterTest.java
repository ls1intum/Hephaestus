package de.tum.in.www1.hephaestus.gitprovider.label.github;

import static org.assertj.core.api.Assertions.*;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayload;
import org.mockito.InjectMocks;

@DisplayName("GitHub Label Converter")
@ExtendWith(GitHubPayloadExtension.class)
class GitHubLabelConverterTest extends BaseUnitTest {

    @InjectMocks
    private GitHubLabelConverter converter;

    @Test
    @DisplayName("Should convert GitHub created label payload with all fields preserved")
    void shouldConvertGitHubCreatedLabelPayloadWithAllFieldsPreserved(
            @GitHubPayload("label.created") GHEventPayload.Label payload) {
        // Given
        var ghLabel = payload.getLabel();

        // When
        Label result = converter.convert(ghLabel);

        // Then
        assertThat(result)
                .as("Converted label should contain all GitHub label data")
                .satisfies(label -> {
                    assertThat(label.getId()).isEqualTo(ghLabel.getId());
                    assertThat(label.getName()).isEqualTo(ghLabel.getName());
                    assertThat(label.getColor()).isEqualTo(ghLabel.getColor());
                    assertThat(label.getDescription()).isEqualTo(ghLabel.getDescription());
                });
    }

    @Test
    @DisplayName("Should convert GitHub edited label payload preserving updates")
    void shouldConvertGitHubEditedLabelPayloadPreservingUpdates(
            @GitHubPayload("label.edited") GHEventPayload.Label payload) {
        // Given
        var ghLabel = payload.getLabel();

        // When
        Label result = converter.convert(ghLabel);

        // Then
        assertThat(result)
                .as("Converted label should reflect edited changes")
                .extracting(Label::getId, Label::getName, Label::getColor, Label::getDescription)
                .containsExactly(
                        ghLabel.getId(),
                        ghLabel.getName(),
                        ghLabel.getColor(),
                        ghLabel.getDescription());
    }

    @Test
    @DisplayName("Should convert GitHub deleted label payload for cleanup operations")
    void shouldConvertGitHubDeletedLabelPayloadForCleanupOperations(
            @GitHubPayload("label.deleted") GHEventPayload.Label payload) {
        // Given
        var ghLabel = payload.getLabel();

        // When
        Label result = converter.convert(ghLabel);

        // Then
        assertThat(result)
                .as("Deleted label conversion should maintain referential integrity")
                .satisfies(label -> {
                    assertThat(label.getId())
                            .as("ID must be preserved for deletion operations")
                            .isEqualTo(ghLabel.getId());
                    assertThat(label.getName())
                            .as("Name must be preserved for audit trail")
                            .isEqualTo(ghLabel.getName());
                });
    }

    @Test
    @DisplayName("Should update existing label instance with new GitHub data")
    void shouldUpdateExistingLabelInstanceWithNewGitHubData(
            @GitHubPayload("label.edited") GHEventPayload.Label payload) {
        // Given
        var ghLabel = payload.getLabel();
        Label existingLabel = new Label();
        existingLabel.setId(999L);
        existingLabel.setName("old-label");
        existingLabel.setColor("ffffff");
        existingLabel.setDescription("old description");

        // When
        Label result = converter.update(ghLabel, existingLabel);

        // Then
        assertThat(result)
                .as("Should return the same instance for object identity")
                .isSameAs(existingLabel);

        assertThat(result)
                .as("All fields should be updated with GitHub data")
                .satisfies(label -> {
                    assertThat(label.getId()).isEqualTo(ghLabel.getId());
                    assertThat(label.getName()).isEqualTo(ghLabel.getName());
                    assertThat(label.getColor()).isEqualTo(ghLabel.getColor());
                    assertThat(label.getDescription()).isEqualTo(ghLabel.getDescription());
                });
    }

    @Test
    @DisplayName("Should handle null description gracefully during conversion")
    void shouldHandleNullDescriptionGracefullyDuringConversion(
            @GitHubPayload("label.created") GHEventPayload.Label payload) {
        // Given
        var ghLabel = payload.getLabel();
        // Note: GitHub API can return null descriptions

        // When
        Label result = converter.convert(ghLabel);

        // Then
        assertThat(result)
                .as("Conversion should handle null description without errors")
                .satisfies(label -> {
                    assertThat(label.getId()).isEqualTo(ghLabel.getId());
                    assertThat(label.getName()).isEqualTo(ghLabel.getName());
                    assertThat(label.getColor()).isEqualTo(ghLabel.getColor());
                    // Description can be null - that's valid GitHub API behavior
                    assertThat(label.getDescription()).isEqualTo(ghLabel.getDescription());
                });
    }
}
