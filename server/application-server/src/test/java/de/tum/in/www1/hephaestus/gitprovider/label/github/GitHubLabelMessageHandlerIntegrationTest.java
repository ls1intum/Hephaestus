package de.tum.in.www1.hephaestus.gitprovider.label.github;

import static org.assertj.core.api.Assertions.*;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("GitHub Label Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
class GitHubLabelMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubLabelMessageHandler handler;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Test
    @DisplayName("Should return correct handler event type")
    void shouldReturnCorrectHandlerEventType() {
        assertThat(handler.getHandlerEvent()).isEqualTo(GHEvent.LABEL);
    }

    @Test
    @DisplayName("Should process created label events")
    void shouldProcessCreatedLabelEventsEndToEnd(@GitHubPayload("label.created") GHEventPayload.Label payload) throws Exception {
        // Given
        var ghLabel = payload.getLabel();
        var ghRepository = payload.getRepository();
        
        // Ensure label doesn't exist initially
        labelRepository.deleteById(ghLabel.getId());
        
        // When
        handler.handleEvent(payload);
        
        // Then
        assertThat(labelRepository.findById(ghLabel.getId()))
            .isPresent()
            .get()
            .satisfies(label -> {
                assertThat(label.getId()).isEqualTo(ghLabel.getId());
                assertThat(label.getName()).isEqualTo(ghLabel.getName());
                assertThat(label.getColor()).isEqualTo(ghLabel.getColor());
                assertThat(label.getDescription()).isEqualTo(ghLabel.getDescription());
            });
        
        // Repository should also be processed
        assertThat(repositoryRepository.findByNameWithOwner(ghRepository.getFullName()))
            .isPresent();
    }

    @Test
    @DisplayName("Should process edited label events")
    void shouldProcessEditedLabelEventsEndToEnd(@GitHubPayload("label.edited") GHEventPayload.Label payload) throws Exception {
        // Given
        var ghLabel = payload.getLabel();
        
        // Create existing label with different data
        Label existingLabel = new Label();
        existingLabel.setId(ghLabel.getId());
        existingLabel.setName("old-name");
        existingLabel.setColor("ffffff");
        existingLabel.setDescription("old description");
        labelRepository.save(existingLabel);
        
        // When
        handler.handleEvent(payload);
        
        // Then
        assertThat(labelRepository.findById(ghLabel.getId()))
            .isPresent()
            .get()
            .satisfies(label -> {
                assertThat(label.getId()).isEqualTo(ghLabel.getId());
                assertThat(label.getName()).isEqualTo(ghLabel.getName());
                assertThat(label.getColor()).isEqualTo(ghLabel.getColor());
                assertThat(label.getDescription()).isEqualTo(ghLabel.getDescription());
            });
    }

    @Test
    @DisplayName("Should process deleted label events")
    void shouldProcessDeletedLabelEventsEndToEnd(@GitHubPayload("label.deleted") GHEventPayload.Label payload) throws Exception {
        // Given
        var ghLabel = payload.getLabel();
        
        // Create existing label
        Label existingLabel = new Label();
        existingLabel.setId(ghLabel.getId());
        existingLabel.setName(ghLabel.getName());
        existingLabel.setColor(ghLabel.getColor());
        labelRepository.save(existingLabel);
        
        // Verify it exists
        assertThat(labelRepository.findById(ghLabel.getId())).isPresent();
        
        // When
        handler.handleEvent(payload);
        
        // Then
        assertThat(labelRepository.findById(ghLabel.getId())).isEmpty();
    }
}
