package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.workspace.exception.InvalidWorkspaceSlugException;
import de.tum.in.www1.hephaestus.workspace.exception.WorkspaceSlugConflictException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
@DisplayName("Workspace Slug Rename Tests")
class WorkspaceSlugRenameTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceSlugHistoryRepository slugHistoryRepository;

    @InjectMocks
    private WorkspaceService workspaceService;

    private Workspace testWorkspace;

    @BeforeEach
    void setUp() {
        testWorkspace = new Workspace();
        testWorkspace.setId(1L);
        testWorkspace.setSlug("old-slug");
        testWorkspace.setDisplayName("Test Workspace");
        testWorkspace.setAccountLogin("test-account");
        testWorkspace.setAccountType(AccountType.USER);
        testWorkspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should successfully rename slug with valid new slug")
    void shouldRenameSlugSuccessfully() {
        // Arrange
        String newSlug = "new-slug";
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
        when(workspaceRepository.existsBySlug(newSlug)).thenReturn(false);
        when(slugHistoryRepository.existsByOldSlug(newSlug)).thenReturn(false);
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Workspace result = workspaceService.renameSlug(1L, newSlug);

        // Assert
        assertThat(result.getSlug()).isEqualTo(newSlug);

        // Verify history was created
        ArgumentCaptor<WorkspaceSlugHistory> historyCaptor = ArgumentCaptor.forClass(WorkspaceSlugHistory.class);
        verify(slugHistoryRepository).save(historyCaptor.capture());

        WorkspaceSlugHistory history = historyCaptor.getValue();
        assertThat(history.getOldSlug()).isEqualTo("old-slug");
        assertThat(history.getNewSlug()).isEqualTo(newSlug);
        assertThat(history.getChangedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should return same workspace when renaming to current slug (no-op)")
    void shouldHandleNoOpRename() {
        // Arrange
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));

        // Act
        Workspace result = workspaceService.renameSlug(1L, "old-slug");

        // Assert
        assertThat(result.getSlug()).isEqualTo("old-slug");
        verify(slugHistoryRepository, never()).save(any());
        verify(workspaceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when workspace not found")
    void shouldThrowExceptionWhenWorkspaceNotFound() {
        // Arrange
        when(workspaceRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> workspaceService.renameSlug(999L, "new-slug"))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Workspace")
            .hasMessageContaining("999");
    }

    @Test
    @DisplayName("Should throw InvalidWorkspaceSlugException for invalid slug pattern")
    void shouldRejectInvalidSlugPattern() {
        // Arrange
        lenient().when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));

        // Act & Assert - too short
        assertThatThrownBy(() -> workspaceService.renameSlug(1L, "ab")).isInstanceOf(
            InvalidWorkspaceSlugException.class
        );

        // Act & Assert - uppercase
        assertThatThrownBy(() -> workspaceService.renameSlug(1L, "New-Slug")).isInstanceOf(
            InvalidWorkspaceSlugException.class
        );

        // Act & Assert - special characters
        assertThatThrownBy(() -> workspaceService.renameSlug(1L, "new_slug")).isInstanceOf(
            InvalidWorkspaceSlugException.class
        );

        // Act & Assert - starts with hyphen
        assertThatThrownBy(() -> workspaceService.renameSlug(1L, "-newslug")).isInstanceOf(
            InvalidWorkspaceSlugException.class
        );
    }

    @Test
    @DisplayName("Should throw WorkspaceSlugConflictException when slug exists in active workspace")
    void shouldRejectConflictWithActiveWorkspace() {
        // Arrange
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
        when(workspaceRepository.existsBySlug("existing-slug")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> workspaceService.renameSlug(1L, "existing-slug"))
            .isInstanceOf(WorkspaceSlugConflictException.class)
            .hasMessageContaining("existing-slug")
            .hasMessageContaining("active workspace");
    }

    @Test
    @DisplayName("Should throw WorkspaceSlugConflictException when slug exists in redirect history")
    void shouldRejectConflictWithExistingRedirect() {
        // Arrange
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
        when(workspaceRepository.existsBySlug("redirected-slug")).thenReturn(false);
        when(slugHistoryRepository.existsByOldSlug("redirected-slug")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> workspaceService.renameSlug(1L, "redirected-slug"))
            .isInstanceOf(WorkspaceSlugConflictException.class)
            .hasMessageContaining("redirected-slug")
            .hasMessageContaining("existing redirect");
    }
}
