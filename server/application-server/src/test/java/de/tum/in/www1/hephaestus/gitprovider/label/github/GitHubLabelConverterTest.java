package de.tum.in.www1.hephaestus.gitprovider.label.github;

import static org.junit.jupiter.api.Assertions.*;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayload;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DisplayName("GitHubLabelConverter Tests")
@ExtendWith({ MockitoExtension.class, GitHubPayloadExtension.class })
class GitHubLabelConverterTest {

    @InjectMocks
    private GitHubLabelConverter converter;

    @Test
    @DisplayName("Should convert real GitHub created label payload")
    void testConvertRealCreatedPayload(@GitHubPayload("label.created") GHEventPayload.Label payload) {
        // Given
        var ghLabel = payload.getLabel();
        
        // When
        Label result = converter.convert(ghLabel);

        // Then
        assertNotNull(result, "Converted label should not be null");
        assertEquals(ghLabel.getId(), result.getId(), "Should preserve GitHub label ID");
        assertEquals(ghLabel.getName(), result.getName(), "Should preserve label name");
        assertEquals(ghLabel.getColor(), result.getColor(), "Should preserve label color");
        assertEquals(ghLabel.getDescription(), result.getDescription(), "Should preserve description");
    }

    @Test
    @DisplayName("Should convert real GitHub edited label payload")
    void testConvertRealEditedPayload(@GitHubPayload("label.edited") GHEventPayload.Label payload) {
        // Given
        var ghLabel = payload.getLabel();
        
        // When
        Label result = converter.convert(ghLabel);

        // Then
        assertNotNull(result, "Converted label should not be null");
        assertEquals(ghLabel.getId(), result.getId(), "Should preserve GitHub label ID");
        assertEquals(ghLabel.getName(), result.getName(), "Should preserve label name");
        assertEquals(ghLabel.getColor(), result.getColor(), "Should preserve label color");
        assertEquals(ghLabel.getDescription(), result.getDescription(), "Should preserve description");
    }

    @Test
    @DisplayName("Should convert real GitHub deleted label payload")
    void testConvertRealDeletedPayload(@GitHubPayload("label.deleted") GHEventPayload.Label payload) {
        // Given
        var ghLabel = payload.getLabel();
        
        // When
        Label result = converter.convert(ghLabel);

        // Then
        assertNotNull(result, "Converted label should not be null");
        assertEquals(ghLabel.getId(), result.getId(), "Should preserve GitHub label ID");
        assertEquals(ghLabel.getName(), result.getName(), "Should preserve label name");
        assertEquals(ghLabel.getColor(), result.getColor(), "Should preserve label color");
        assertEquals(ghLabel.getDescription(), result.getDescription(), "Should preserve description");
    }

    @Test
    @DisplayName("Should update existing label with new data")
    void testUpdateExistingLabel(@GitHubPayload("label.edited") GHEventPayload.Label payload) {
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
        assertSame(existingLabel, result, "Should return the same instance");
        assertEquals(ghLabel.getId(), result.getId(), "Should update to new GitHub ID");
        assertEquals(ghLabel.getName(), result.getName(), "Should update to new name");
        assertEquals(ghLabel.getColor(), result.getColor(), "Should update to new color");
        assertEquals(ghLabel.getDescription(), result.getDescription(), "Should update to new description");
    }
}
