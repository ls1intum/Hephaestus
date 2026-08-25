package de.tum.cit.aet.hephaestus.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import de.tum.cit.aet.hephaestus.activity.scoring.ExperiencePointProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit tests for ActivityEventService.
 */
class ActivityEventServiceTest extends BaseUnitTest {

    @Mock
    private ActivityEventRepository eventRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private ExperiencePointProperties xpProperties;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MeterRegistry meterRegistry;
    private ActivityEventService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // Use lenient stubbing since not all tests exercise XP clamping path
        lenient().when(xpProperties.maxXpPerEvent()).thenReturn(1000.0);
        service = new ActivityEventService(
            eventRepository,
            workspaceRepository,
            xpProperties,
            meterRegistry,
            eventPublisher
        );
    }

    @Test
    void record_success_savesEvent() {
        when(workspaceRepository.existsById(1L)).thenReturn(true);
        // insertIfAbsent returns 1 = event was inserted
        when(
            eventRepository.insertIfAbsent(
                any(UUID.class),
                anyString(),
                anyString(),
                any(Instant.class),
                any(),
                eq(1L),
                any(),
                anyString(),
                anyLong(),
                anyDouble()
            )
        ).thenReturn(1);

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

        assertThat(result).isTrue();
        verify(eventRepository).insertIfAbsent(
            any(UUID.class),
            anyString(),
            anyString(),
            any(Instant.class),
            any(),
            eq(1L),
            any(),
            anyString(),
            anyLong(),
            anyDouble()
        );
        assertThat(meterRegistry.counter("activity.events.recorded").count()).isEqualTo(1.0);
    }

    @Test
    void record_duplicate_returnsFalse() {
        when(workspaceRepository.existsById(1L)).thenReturn(true);
        // insertIfAbsent returns 0 = duplicate (ON CONFLICT DO NOTHING)
        when(
            eventRepository.insertIfAbsent(
                any(UUID.class),
                anyString(),
                anyString(),
                any(Instant.class),
                any(),
                eq(1L),
                any(),
                anyString(),
                anyLong(),
                anyDouble()
            )
        ).thenReturn(0);

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

        assertThat(result).isFalse();
        assertThat(meterRegistry.counter("activity.events.recorded").count()).isEqualTo(0.0);
        assertThat(meterRegistry.counter("activity.events.duplicate").count()).isEqualTo(1.0);
    }

    @Test
    void record_workspaceNotFound_returnsFalse() {
        when(workspaceRepository.existsById(999L)).thenReturn(false);

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

        assertThat(result).isFalse();
        verifyNoInteractions(eventRepository);
    }

    @Test
    void record_negativeXp_clampsToZero() {
        when(workspaceRepository.existsById(1L)).thenReturn(true);
        // Capture the XP value passed to insertIfAbsent
        when(
            eventRepository.insertIfAbsent(
                any(UUID.class),
                anyString(),
                anyString(),
                any(Instant.class),
                any(),
                eq(1L),
                any(),
                anyString(),
                anyLong(),
                eq(0.0)
            )
        ).thenReturn(1);

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

        assertThat(result).isTrue();
        // Verify XP was clamped to 0.0
        verify(eventRepository).insertIfAbsent(
            any(UUID.class),
            anyString(),
            anyString(),
            any(Instant.class),
            any(),
            eq(1L),
            any(),
            anyString(),
            anyLong(),
            eq(0.0)
        );
    }

    @Test
    void record_excessiveXp_clampsToMax() {
        when(workspaceRepository.existsById(1L)).thenReturn(true);
        // Verify XP was clamped to max (1000.0 as configured in setUp)
        when(
            eventRepository.insertIfAbsent(
                any(UUID.class),
                anyString(),
                anyString(),
                any(Instant.class),
                any(),
                eq(1L),
                any(),
                anyString(),
                anyLong(),
                eq(1000.0)
            )
        ).thenReturn(1);

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

        assertThat(result).isTrue();
        // Verify XP was clamped to 1000.0
        verify(eventRepository).insertIfAbsent(
            any(UUID.class),
            anyString(),
            anyString(),
            any(Instant.class),
            any(),
            eq(1L),
            any(),
            anyString(),
            anyLong(),
            eq(1000.0)
        );
    }
}
