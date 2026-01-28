package de.tum.in.www1.hephaestus.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.activity.scoring.ExperiencePointProperties;
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

    @Mock
    private ExperiencePointProperties xpProperties;

    private MeterRegistry meterRegistry;
    private ActivityEventService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // Use lenient stubbing since not all tests exercise XP clamping path
        lenient().when(xpProperties.maxXpPerEvent()).thenReturn(1000.0);
        service = new ActivityEventService(eventRepository, workspaceRepository, xpProperties, meterRegistry);
    }

    @Test
    @DisplayName("record saves event and returns true on success")
    void record_success_savesEvent() {
        // Arrange
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        // Pre-check returns false = event doesn't exist yet
        when(eventRepository.existsByWorkspaceIdAndEventKey(eq(1L), anyString())).thenReturn(false);
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
            1.0
        );

        // Assert
        assertThat(result).isTrue();
        verify(eventRepository).save(any(ActivityEvent.class));
        assertThat(meterRegistry.counter("activity.events.recorded").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("record returns false via pre-check when event already exists")
    void record_duplicatePreCheck_returnsFalseWithoutSave() {
        // Arrange
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        Instant timestamp = Instant.now();
        String expectedEventKey = ActivityEvent.buildKey(ActivityEventType.PULL_REQUEST_OPENED, 100L, timestamp);

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        // Pre-check returns true = event already exists
        when(eventRepository.existsByWorkspaceIdAndEventKey(1L, expectedEventKey)).thenReturn(true);

        // Act
        boolean result = service.record(
            1L,
            ActivityEventType.PULL_REQUEST_OPENED,
            timestamp,
            null,
            null,
            ActivityTargetType.PULL_REQUEST,
            100L,
            1.0
        );

        // Assert
        assertThat(result).isFalse();
        // save() should NEVER be called when pre-check detects duplicate
        verify(eventRepository, never()).save(any(ActivityEvent.class));
        assertThat(meterRegistry.counter("activity.events.recorded").count()).isEqualTo(0.0);
        assertThat(meterRegistry.counter("activity.events.duplicate").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName(
        "record returns false and increments duplicate counter on DataIntegrityViolation (race condition fallback)"
    )
    void record_duplicate_returnsFalseAndIncrementsDuplicateCounter() {
        // This test covers the race condition case where pre-check passes but save still fails
        // due to concurrent insert between the check and save.
        // Arrange
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        Instant timestamp = Instant.now();
        String expectedEventKey = ActivityEvent.buildKey(ActivityEventType.PULL_REQUEST_OPENED, 100L, timestamp);

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        // Pre-check returns false = event doesn't exist yet
        when(eventRepository.existsByWorkspaceIdAndEventKey(1L, expectedEventKey)).thenReturn(false);
        // But save throws due to race condition (another thread inserted between check and save)
        when(eventRepository.save(any(ActivityEvent.class))).thenThrow(
            new DataIntegrityViolationException("Unique constraint violation")
        );

        // Act
        boolean result = service.record(
            1L,
            ActivityEventType.PULL_REQUEST_OPENED,
            timestamp,
            null,
            null,
            ActivityTargetType.PULL_REQUEST,
            100L,
            1.0
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
            1.0
        );

        // Assert
        assertThat(result).isFalse();
        verifyNoInteractions(eventRepository);
    }

    @Test
    @DisplayName("record clamps negative XP to zero")
    void record_negativeXp_clampsToZero() {
        // Arrange
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(eventRepository.existsByWorkspaceIdAndEventKey(eq(1L), anyString())).thenReturn(false);
        when(eventRepository.save(any(ActivityEvent.class))).thenAnswer(inv -> {
            ActivityEvent event = inv.getArgument(0);
            // Verify XP was clamped to 0
            assertThat(event.getXp()).isEqualTo(0.0);
            return event;
        });

        // Act
        boolean result = service.record(
            1L,
            ActivityEventType.PULL_REQUEST_OPENED,
            Instant.now(),
            null,
            null,
            ActivityTargetType.PULL_REQUEST,
            100L,
            -50.0 // negative XP
        );

        // Assert
        assertThat(result).isTrue();
        verify(eventRepository).save(any(ActivityEvent.class));
    }

    @Test
    @DisplayName("record clamps XP above maximum to maxXpPerEvent")
    void record_excessiveXp_clampsToMax() {
        // Arrange
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(eventRepository.existsByWorkspaceIdAndEventKey(eq(1L), anyString())).thenReturn(false);
        when(eventRepository.save(any(ActivityEvent.class))).thenAnswer(inv -> {
            ActivityEvent event = inv.getArgument(0);
            // Verify XP was clamped to max (1000.0 as configured in setUp)
            assertThat(event.getXp()).isEqualTo(1000.0);
            return event;
        });

        // Act
        boolean result = service.record(
            1L,
            ActivityEventType.PULL_REQUEST_OPENED,
            Instant.now(),
            null,
            null,
            ActivityTargetType.PULL_REQUEST,
            100L,
            9999.0 // excessive XP
        );

        // Assert
        assertThat(result).isTrue();
        verify(eventRepository).save(any(ActivityEvent.class));
    }
}
