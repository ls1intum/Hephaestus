package de.tum.cit.aet.hephaestus.achievement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.activity.ActivityEventType;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AchievementRegistry}.
 * Validates YAML loading, parent validation, and trigger event wiring.
 */
class AchievementRegistryTest extends BaseUnitTest {

    private AchievementRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AchievementRegistry();
        registry.init();
    }

    @Nested
    class YamlLoadingTests {

        @Test
        void loadsAllAchievements() {
            assertThat(registry.values()).isNotEmpty();
            assertThat(registry.getAchievementIds()).isNotEmpty();
        }

        @Test
        void getByIdReturnsCorrectAchievement() {
            AchievementDefinition def = registry.getById("pr.merged.common.1");
            assertThat(def.id()).isEqualTo("pr.merged.common.1");
            assertThat(def.category()).isEqualTo(AchievementCategory.PULL_REQUESTS);
            assertThat(def.rarity()).isEqualTo(AchievementRarity.COMMON);
        }

        @Test
        void getByIdThrowsForUnknownId() {
            assertThatThrownBy(() -> registry.getById("nonexistent.achievement")).isInstanceOf(
                IllegalArgumentException.class
            );
        }
    }

    @Nested
    class CommitTriggerTests {

        @Test
        void allCommitAchievementsHaveCommitCreatedTrigger() {
            List<String> commitMainlineIds = List.of(
                "commit.common.1",
                "commit.common.2",
                "commit.uncommon.1",
                "commit.uncommon.2",
                "commit.rare",
                "commit.epic",
                "commit.legendary",
                "commit.mythic"
            );

            for (String id : commitMainlineIds) {
                AchievementDefinition def = registry.getById(id);
                assertThat(def.triggerEvents())
                    .as("Achievement '%s' should have COMMIT_CREATED trigger", id)
                    .contains(ActivityEventType.COMMIT_CREATED);
            }
        }

        @Test
        void getByTriggerEventReturnsCommitAchievements() {
            List<AchievementDefinition> commitAchievements = registry.getByTriggerEvent(
                ActivityEventType.COMMIT_CREATED
            );

            assertThat(commitAchievements)
                .extracting(AchievementDefinition::id)
                .contains(
                    "commit.common.1",
                    "commit.common.2",
                    "commit.uncommon.1",
                    "commit.uncommon.2",
                    "commit.rare",
                    "commit.epic",
                    "commit.legendary",
                    "commit.mythic"
                );
        }
    }

    @Nested
    @DisplayName("Parent References")
    class ParentReferenceTests {

        private static final Set<String> STANDALONE_SELF_REFERENCED_ACHIEVEMENTS = Set.of(
            "issue.special.necromancer",
            "milestone.long_time_return",
            "milestone.all_rare"
        );

        @Test
        void shouldOnlyReferenceItselfWhenAchievementIsStandalone() {
            for (AchievementDefinition def : registry.values()) {
                if (def.parent() != null && !def.parent().isEmpty()) {
                    if (def.parent().equals(def.id())) {
                        assertThat(STANDALONE_SELF_REFERENCED_ACHIEVEMENTS)
                            .as("Only standalone achievements may self-reference: '%s'", def.id())
                            .contains(def.id());
                    }
                }
            }
        }

        @Test
        void shouldHaveExistingParentWhenParentIsSpecified() {
            Set<String> allIds = registry.getAchievementIds();
            for (AchievementDefinition def : registry.values()) {
                if (def.parent() != null && !def.parent().isEmpty() && !def.parent().equals(def.id())) {
                    assertThat(allIds)
                        .as("Parent '%s' of achievement '%s' must exist", def.parent(), def.id())
                        .contains(def.parent());
                }
            }
        }

        @Test
        @DisplayName("no non-standalone parent cycles exist in the hierarchy")
        void shouldNotFormCyclesWhenFollowingParentChain() {
            for (AchievementDefinition def : registry.values()) {
                Set<String> visited = new HashSet<>();
                visited.add(def.id());
                String current = def.parent();
                while (current != null && !current.isEmpty() && !current.equals(def.id())) {
                    assertThat(visited.add(current))
                        .as("Cycle detected: achievement '%s' is part of cycle through '%s'", def.id(), current)
                        .isTrue();
                    AchievementDefinition parentDef = registry.getById(current);
                    if (parentDef.parent() != null && parentDef.parent().equals(current)) {
                        break;
                    }
                    current = parentDef.parent();
                }
            }
        }
    }

    @Nested
    class EvaluatorTriggerTests {

        @Test
        void dummyEvaluatorHasNoTriggers() {
            for (AchievementDefinition def : registry.values()) {
                if ("DummyEvaluator".equals(def.evaluatorClass())) {
                    assertThat(def.triggerEvents())
                        .as(
                            "DummyEvaluator achievement '%s' should have empty triggerEvents to avoid wasted work",
                            def.id()
                        )
                        .satisfiesAnyOf(
                            triggers -> assertThat(triggers).isNull(),
                            triggers -> assertThat(triggers).isEmpty()
                        );
                }
            }
        }

        @Test
        @DisplayName("non-DummyEvaluator achievements have at least one trigger event")
        void realEvaluatorsHaveTriggers() {
            for (AchievementDefinition def : registry.values()) {
                if (!"DummyEvaluator".equals(def.evaluatorClass())) {
                    assertThat(def.triggerEvents())
                        .as(
                            "Achievement '%s' with evaluator '%s' must have trigger events",
                            def.id(),
                            def.evaluatorClass()
                        )
                        .isNotNull()
                        .isNotEmpty();
                }
            }
        }
    }

    @Nested
    class MilestoneFirstActionTests {

        @Test
        void includesPullRequestMergedTrigger() {
            AchievementDefinition def = registry.getById("milestone.first_action");
            assertThat(def.triggerEvents()).contains(ActivityEventType.PULL_REQUEST_MERGED);
        }

        @Test
        void includesCommitCreatedTrigger() {
            AchievementDefinition def = registry.getById("milestone.first_action");
            assertThat(def.triggerEvents()).contains(ActivityEventType.COMMIT_CREATED);
        }
    }
}
