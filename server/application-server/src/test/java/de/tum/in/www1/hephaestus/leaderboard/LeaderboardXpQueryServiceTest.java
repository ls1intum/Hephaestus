package de.tum.in.www1.hephaestus.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.activity.ActivityBreakdownProjection;
import de.tum.in.www1.hephaestus.activity.ActivityEventRepository;
import de.tum.in.www1.hephaestus.activity.ActivityEventType;
import de.tum.in.www1.hephaestus.activity.ActivityXpProjection;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for LeaderboardXpQueryService.
 *
 * <p>Tests the CQRS read path that aggregates XP from the activity event ledger.
 * Verifies correct data hydration and breakdown stat accumulation.
 */
@Tag("unit")
@DisplayName("LeaderboardXpQueryService")
@ExtendWith(MockitoExtension.class)
class LeaderboardXpQueryServiceTest {

    private static final Long WORKSPACE_ID = 1L;
    private static final Instant SINCE = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant UNTIL = Instant.parse("2024-01-08T00:00:00Z");

    @Mock
    private ActivityEventRepository activityEventRepository;

    @Mock
    private UserRepository userRepository;

    private LeaderboardXpQueryService service;

    @BeforeEach
    void setUp() {
        service = new LeaderboardXpQueryService(activityEventRepository, userRepository);
    }

    @Nested
    @DisplayName("getLeaderboardData")
    class GetLeaderboardDataTests {

        @Test
        @DisplayName("returns empty map when no activity events exist")
        void returnsEmptyMapWhenNoEvents() {
            // Arrange
            when(
                activityEventRepository.findExperiencePointsByWorkspaceAndTimeframe(WORKSPACE_ID, SINCE, UNTIL)
            ).thenReturn(List.of());

            // Act
            Map<Long, LeaderboardUserXp> result = service.getLeaderboardData(WORKSPACE_ID, SINCE, UNTIL);

            // Assert
            assertThat(result).isEmpty();
            verify(activityEventRepository, never()).findActivityBreakdown(any(), any(), any(), any());
        }

        @Test
        @DisplayName("aggregates XP totals from activity events")
        void aggregatesXpTotals() {
            // Arrange
            User user1 = createUser(100L, "alice");
            User user2 = createUser(200L, "bob");

            List<ActivityXpProjection> xpData = List.of(
                createXpProjection(100L, 150.0, 10L),
                createXpProjection(200L, 75.0, 5L)
            );

            when(
                activityEventRepository.findExperiencePointsByWorkspaceAndTimeframe(WORKSPACE_ID, SINCE, UNTIL)
            ).thenReturn(xpData);
            when(
                activityEventRepository.findActivityBreakdown(eq(WORKSPACE_ID), anySet(), eq(SINCE), eq(UNTIL))
            ).thenReturn(List.of());
            when(userRepository.findAllById(Set.of(100L, 200L))).thenReturn(List.of(user1, user2));

            // Act
            Map<Long, LeaderboardUserXp> result = service.getLeaderboardData(WORKSPACE_ID, SINCE, UNTIL);

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result.get(100L).totalScore()).isEqualTo(150);
            assertThat(result.get(100L).eventCount()).isEqualTo(10);
            assertThat(result.get(200L).totalScore()).isEqualTo(75);
            assertThat(result.get(200L).eventCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("enriches data with activity breakdown by event type")
        void enrichesWithBreakdown() {
            // Arrange
            User user = createUser(100L, "alice");

            List<ActivityXpProjection> xpData = List.of(createXpProjection(100L, 100.0, 8L));

            List<ActivityBreakdownProjection> breakdown = List.of(
                createBreakdownProjection(100L, ActivityEventType.REVIEW_APPROVED, 3L),
                createBreakdownProjection(100L, ActivityEventType.REVIEW_CHANGES_REQUESTED, 2L),
                createBreakdownProjection(100L, ActivityEventType.REVIEW_COMMENTED, 1L),
                createBreakdownProjection(100L, ActivityEventType.COMMENT_CREATED, 1L),
                createBreakdownProjection(100L, ActivityEventType.REVIEW_COMMENT_CREATED, 1L)
            );

            when(
                activityEventRepository.findExperiencePointsByWorkspaceAndTimeframe(WORKSPACE_ID, SINCE, UNTIL)
            ).thenReturn(xpData);
            when(
                activityEventRepository.findActivityBreakdown(eq(WORKSPACE_ID), anySet(), eq(SINCE), eq(UNTIL))
            ).thenReturn(breakdown);
            when(userRepository.findAllById(Set.of(100L))).thenReturn(List.of(user));

            // Act
            Map<Long, LeaderboardUserXp> result = service.getLeaderboardData(WORKSPACE_ID, SINCE, UNTIL);

            // Assert
            LeaderboardUserXp data = result.get(100L);
            assertThat(data).isNotNull();
            assertThat(data.approvals()).isEqualTo(3);
            assertThat(data.changeRequests()).isEqualTo(2);
            assertThat(data.comments()).isEqualTo(1);
            assertThat(data.issueComments()).isEqualTo(1);
            assertThat(data.codeComments()).isEqualTo(1);
        }

        @Test
        @DisplayName("skips actors without matching user entity")
        void skipsUnknownActors() {
            // Arrange
            User knownUser = createUser(100L, "alice");

            List<ActivityXpProjection> xpData = List.of(
                createXpProjection(100L, 50.0, 5L),
                createXpProjection(999L, 100.0, 10L) // Unknown user
            );

            when(
                activityEventRepository.findExperiencePointsByWorkspaceAndTimeframe(WORKSPACE_ID, SINCE, UNTIL)
            ).thenReturn(xpData);
            when(
                activityEventRepository.findActivityBreakdown(eq(WORKSPACE_ID), anySet(), eq(SINCE), eq(UNTIL))
            ).thenReturn(List.of());
            when(userRepository.findAllById(Set.of(100L, 999L))).thenReturn(List.of(knownUser));

            // Act
            Map<Long, LeaderboardUserXp> result = service.getLeaderboardData(WORKSPACE_ID, SINCE, UNTIL);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.containsKey(100L)).isTrue();
            assertThat(result.containsKey(999L)).isFalse();
        }

        @Test
        @DisplayName("filters by team IDs when provided")
        void filtersByTeamIds() {
            // Arrange
            User user = createUser(100L, "alice");
            Set<Long> teamIds = Set.of(10L, 20L);

            List<ActivityXpProjection> xpData = List.of(createXpProjection(100L, 50.0, 5L));

            when(
                activityEventRepository.findExperiencePointsByWorkspaceAndTeamsAndTimeframe(
                    WORKSPACE_ID,
                    teamIds,
                    SINCE,
                    UNTIL
                )
            ).thenReturn(xpData);
            when(
                activityEventRepository.findActivityBreakdown(eq(WORKSPACE_ID), anySet(), eq(SINCE), eq(UNTIL))
            ).thenReturn(List.of());
            when(userRepository.findAllById(Set.of(100L))).thenReturn(List.of(user));

            // Act
            Map<Long, LeaderboardUserXp> result = service.getLeaderboardData(WORKSPACE_ID, SINCE, UNTIL, teamIds);

            // Assert
            assertThat(result).hasSize(1);
            verify(activityEventRepository).findExperiencePointsByWorkspaceAndTeamsAndTimeframe(
                WORKSPACE_ID,
                teamIds,
                SINCE,
                UNTIL
            );
            verify(activityEventRepository, never()).findExperiencePointsByWorkspaceAndTimeframe(any(), any(), any());
        }

        @Test
        @DisplayName("handles null XP and event count gracefully")
        void handlesNullValues() {
            // Arrange
            User user = createUser(100L, "alice");
            ActivityXpProjection xpWithNulls = createXpProjection(100L, null, null);

            when(
                activityEventRepository.findExperiencePointsByWorkspaceAndTimeframe(WORKSPACE_ID, SINCE, UNTIL)
            ).thenReturn(List.of(xpWithNulls));
            when(
                activityEventRepository.findActivityBreakdown(eq(WORKSPACE_ID), anySet(), eq(SINCE), eq(UNTIL))
            ).thenReturn(List.of());
            when(userRepository.findAllById(Set.of(100L))).thenReturn(List.of(user));

            // Act
            Map<Long, LeaderboardUserXp> result = service.getLeaderboardData(WORKSPACE_ID, SINCE, UNTIL);

            // Assert
            LeaderboardUserXp data = result.get(100L);
            assertThat(data.totalScore()).isZero();
            assertThat(data.eventCount()).isZero();
        }
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private User createUser(Long id, String login) {
        User user = new User();
        user.setId(id);
        user.setLogin(login);
        return user;
    }

    private ActivityXpProjection createXpProjection(Long actorId, Double totalXp, Long eventCount) {
        return new ActivityXpProjection() {
            @Override
            public Long getActorId() {
                return actorId;
            }

            @Override
            public Double getTotalExperiencePoints() {
                return totalXp;
            }

            @Override
            public Long getEventCount() {
                return eventCount;
            }
        };
    }

    private ActivityBreakdownProjection createBreakdownProjection(
        Long actorId,
        ActivityEventType eventType,
        Long count
    ) {
        return new ActivityBreakdownProjection() {
            @Override
            public Long getActorId() {
                return actorId;
            }

            @Override
            public ActivityEventType getEventType() {
                return eventType;
            }

            @Override
            public Long getCount() {
                return count;
            }

            @Override
            public Double getExperiencePoints() {
                return 0.0;
            }
        };
    }
}
