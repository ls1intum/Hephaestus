package de.tum.in.www1.hephaestus.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for ActivityEventService.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ActivityEventServiceTest {

    @Mock
    private ActivityEventRepository eventRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    private MeterRegistry meterRegistry;
    private ActivityEventService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new ActivityEventService(eventRepository, workspaceRepository, meterRegistry);
    }

    @Test
    @DisplayName("record saves event and returns true on success")
    void record_success_savesEvent() {
        // Arrange
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(eventRepository.save(any(ActivityEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        boolean result = service.record(
            1L,
            ActivityEventType.PULL_REQUEST_OPENED,
            Instant.now(),
            null,
            null,
            ActivityTargetType.PULL_REQUEST,
            100L,
            1.0,
            SourceSystem.GITHUB
        );

        // Assert
        assertThat(result).isTrue();
        verify(eventRepository).save(any(ActivityEvent.class));
        assertThat(meterRegistry.counter("activity.events.recorded").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("record returns false and increments duplicate counter on DataIntegrityViolation")
    void record_duplicate_returnsFalseAndIncrementsDuplicateCounter() {
        // Arrange
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(eventRepository.save(any(ActivityEvent.class))).thenThrow(
            new DataIntegrityViolationException("Unique constraint violation")
        );

        // Act
        boolean result = service.record(
            1L,
            ActivityEventType.PULL_REQUEST_OPENED,
            Instant.now(),
            null,
            null,
            ActivityTargetType.PULL_REQUEST,
            100L,
            1.0,
            SourceSystem.GITHUB
        );

        // Assert
        assertThat(result).isFalse();
        assertThat(meterRegistry.counter("activity.events.recorded").count()).isEqualTo(0.0);
        assertThat(meterRegistry.counter("activity.events.duplicate").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("record returns false when workspace not found")
    void record_workspaceNotFound_returnsFalse() {
        // Arrange
        when(workspaceRepository.findById(999L)).thenReturn(Optional.empty());

        // Act
        boolean result = service.record(
            999L,
            ActivityEventType.PULL_REQUEST_OPENED,
            Instant.now(),
            null,
            null,
            ActivityTargetType.PULL_REQUEST,
            100L,
            1.0,
            SourceSystem.GITHUB
        );

        // Assert
        assertThat(result).isFalse();
        verifyNoInteractions(eventRepository);
    }
}
