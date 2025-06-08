package de.tum.in.www1.hephaestus.gitprovider.label.github;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import java.lang.reflect.Constructor;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHLabel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("GitHubLabelMessageHandler Tests")
@ExtendWith({ MockitoExtension.class, GitHubPayloadExtension.class })
class GitHubLabelMessageHandlerTest {

    @Mock
    private LabelRepository labelRepository;

    @Mock
    private GitHubLabelSyncService labelSyncService;

    @Mock
    private GitHubRepositorySyncService repositorySyncService;

    private GitHubLabelMessageHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        // Use reflection to create handler since constructor is private
        Constructor<GitHubLabelMessageHandler> constructor =
            GitHubLabelMessageHandler.class.getDeclaredConstructor(
                    LabelRepository.class,
                    GitHubLabelSyncService.class,
                    GitHubRepositorySyncService.class
                );
        constructor.setAccessible(true);
        handler = constructor.newInstance(labelRepository, labelSyncService, repositorySyncService);
    }

    @Test
    @DisplayName("Should return correct handler event type")
    void testGetHandlerEvent() {
        assertEquals(GHEvent.LABEL, handler.getHandlerEvent());
    }

    @Test
    @DisplayName("Should handle created events correctly")
    void testHandleCreatedEvent(@GitHubPayload("label.created") GHEventPayload.Label payload) throws Exception {
        handler.handleEvent(payload);

        verify(repositorySyncService).processRepository(payload.getRepository());
        verify(labelSyncService).processLabel(payload.getLabel());
        verify(labelRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Should handle edited events correctly")
    void testHandleEditedEvent(@GitHubPayload("label.edited") GHEventPayload.Label payload) throws Exception {
        handler.handleEvent(payload);

        verify(repositorySyncService).processRepository(payload.getRepository());
        verify(labelSyncService).processLabel(payload.getLabel());
        verify(labelRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Should handle deleted events correctly")
    void testHandleDeletedEvent(@GitHubPayload("label.deleted") GHEventPayload.Label payload) throws Exception {
        handler.handleEvent(payload);

        verify(repositorySyncService).processRepository(payload.getRepository());
        verify(labelRepository).deleteById(8747406390L);
        verify(labelSyncService, never()).processLabel(any());
    }

    @Nested
    @DisplayName("processLabel Real Data Processing")
    class ProcessLabelTests {

        @Mock
        private RepositoryRepository repositoryRepository;

        @Mock
        private GitHubLabelConverter labelConverter;

        private GitHubLabelSyncService syncService;

        @BeforeEach
        void setUp() {
            syncService = new GitHubLabelSyncService(labelRepository, repositoryRepository, labelConverter);
        }

        @Test
        @DisplayName("Should process new label with correct data from created payload")
        void testProcessNewLabelWithCreatedPayload(@GitHubPayload("label.created") GHEventPayload.Label payload) {
            // Setup - label doesn't exist in database yet
            GHLabel ghLabel = payload.getLabel();
            Label convertedLabel = new Label();
            Repository repository = new Repository();
            
            when(labelRepository.findById(8747399111L)).thenReturn(Optional.empty());
            when(labelConverter.convert(ghLabel)).thenReturn(convertedLabel);
            when(repositoryRepository.findByNameWithOwner("HephaestusTest/TestRepository"))
                .thenReturn(Optional.of(repository));
            when(labelRepository.save(convertedLabel)).thenReturn(convertedLabel);

            // Execute
            Label result = syncService.processLabel(ghLabel);

            // Verify correct methods called with actual payload data
            verify(labelRepository).findById(8747399111L);
            verify(labelConverter).convert(ghLabel);
            verify(repositoryRepository).findByNameWithOwner("HephaestusTest/TestRepository");
            verify(labelRepository).save(convertedLabel);
            assertSame(convertedLabel, result);
            assertSame(repository, convertedLabel.getRepository());
        }

        @Test
        @DisplayName("Should process existing label with correct data from edited payload")  
        void testProcessExistingLabelWithEditedPayload(@GitHubPayload("label.edited") GHEventPayload.Label payload) {
            // Setup - label already exists in database
            GHLabel ghLabel = payload.getLabel();
            Label existingLabel = new Label();
            Label updatedLabel = new Label();
            Repository repository = new Repository();
            
            when(labelRepository.findById(8747406390L)).thenReturn(Optional.of(existingLabel));
            when(labelConverter.update(ghLabel, existingLabel)).thenReturn(updatedLabel);
            when(repositoryRepository.findByNameWithOwner("HephaestusTest/TestRepository"))
                .thenReturn(Optional.of(repository));
            when(labelRepository.save(updatedLabel)).thenReturn(updatedLabel);

            // Execute
            Label result = syncService.processLabel(ghLabel);

            // Verify correct methods called with actual payload data
            verify(labelRepository).findById(8747406390L);
            verify(labelConverter).update(ghLabel, existingLabel);
            verify(repositoryRepository).findByNameWithOwner("HephaestusTest/TestRepository");
            verify(labelRepository).save(updatedLabel);
            assertSame(updatedLabel, result);
            assertSame(repository, updatedLabel.getRepository());
        }

        @Test
        @DisplayName("Should handle missing repository gracefully")
        void testProcessLabelWithMissingRepository(@GitHubPayload("label.created") GHEventPayload.Label payload) {
            // Setup - repository doesn't exist in our database
            GHLabel ghLabel = payload.getLabel();
            Label convertedLabel = new Label();
            
            when(labelRepository.findById(8747399111L)).thenReturn(Optional.empty());
            when(labelConverter.convert(ghLabel)).thenReturn(convertedLabel);
            when(repositoryRepository.findByNameWithOwner("HephaestusTest/TestRepository"))
                .thenReturn(Optional.empty());
            when(labelRepository.save(convertedLabel)).thenReturn(convertedLabel);

            // Execute
            Label result = syncService.processLabel(ghLabel);

            // Verify it still processes but without repository link
            verify(repositoryRepository).findByNameWithOwner("HephaestusTest/TestRepository");
            verify(labelRepository).save(convertedLabel);
            assertSame(convertedLabel, result);
            assertNull(convertedLabel.getRepository());
        }
    }
}
