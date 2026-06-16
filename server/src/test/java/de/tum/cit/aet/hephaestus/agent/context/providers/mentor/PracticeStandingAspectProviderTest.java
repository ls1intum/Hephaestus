package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository.GoalStandingRow;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Polarity;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.cache.CacheManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PracticeStandingAspectProviderTest extends BaseUnitTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PracticeFindingRepository findingRepository;

    @Mock
    PracticeRepository practiceRepository;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    CacheManager cacheManager;

    @InjectMocks
    PracticeStandingAspectProvider provider;

    private JsonNode build(List<Practice> spine, List<GoalStandingRow> rows) {
        User user = new User();
        user.setLogin("student");
        when(userRepository.findById(eq(7L))).thenReturn(Optional.of(user));
        when(practiceRepository.findByWorkspaceIdAndActiveTrue(eq(1L))).thenReturn(spine);
        when(findingRepository.findGoalStandingByDeveloperAndWorkspace(eq(7L), eq(1L), any(), any())).thenReturn(rows);
        return provider.buildPayload(1L, 7L);
    }

    private static Practice practice(String goalSlug, String goalName) {
        PracticeArea g = new PracticeArea();
        g.setSlug(goalSlug);
        g.setName(goalName);
        Practice p = new Practice();
        p.setGoal(g);
        return p;
    }

    private static GoalStandingRow row(
        String slug,
        String name,
        Polarity pol,
        Observation v,
        Severity sev,
        long count,
        long recent
    ) {
        return new GoalStandingRow() {
            @Override
            public String getGoalSlug() {
                return slug;
            }

            @Override
            public String getGoalName() {
                return name;
            }

            @Override
            public Polarity getPolarity() {
                return pol;
            }

            @Override
            public Observation getVerdict() {
                return v;
            }

            @Override
            public Severity getSeverity() {
                return sev;
            }

            @Override
            public Long getCount() {
                return count;
            }

            @Override
            public Long getRecentCount() {
                return recent;
            }
        };
    }

    private static JsonNode goal(JsonNode root, String slug) {
        for (JsonNode g : root.get("goals")) {
            if (slug.equals(g.get("goalSlug").asString())) {
                return g;
            }
        }
        throw new AssertionError("goal not present: " + slug);
    }

    @Test
    @DisplayName("all-NA goal is BLIND and excluded from priorities")
    void allNaGoalIsBlind() {
        List<Practice> spine = List.of(practice("constructive-code-review", "Reviewing constructively"));
        List<GoalStandingRow> rows = List.of(
            row(
                "constructive-code-review",
                "Reviewing constructively",
                Polarity.DESIRABLE,
                Observation.NOT_APPLICABLE,
                Severity.INFO,
                5,
                0
            )
        );
        JsonNode root = build(spine, rows);

        JsonNode g = goal(root, "constructive-code-review");
        assertThat(g.get("assessmentState").asString()).isEqualTo("BLIND");
        assertThat(g.get("flaggedCount").asInt()).isZero();
        assertThat(root.get("priorities")).isEmpty();
    }

    @Test
    @DisplayName("flag-only DESIRABLE goal has praiseChannelOpen=false (affirmation asymmetry guard)")
    void flagOnlyGoalHasPraiseChannelClosed() {
        List<Practice> spine = List.of(practice("robust-error-handling", "Handling failure robustly"));
        List<GoalStandingRow> rows = List.of(
            row(
                "robust-error-handling",
                "Handling failure robustly",
                Polarity.DESIRABLE,
                Observation.NOT_OBSERVED,
                Severity.MAJOR,
                3,
                1
            )
        );
        JsonNode root = build(spine, rows);

        JsonNode g = goal(root, "robust-error-handling");
        assertThat(g.get("assessmentState").asString()).isEqualTo("ASSESSED");
        assertThat(g.get("praiseChannelOpen").asBoolean()).isFalse();
        assertThat(g.get("flaggedCount").asInt()).isEqualTo(3);
        assertThat(g.get("affirmedCount").asInt()).isZero();
    }

    @Test
    @DisplayName("an OBSERVED finding opens the praise channel")
    void affirmedGoalOpensPraiseChannel() {
        List<Practice> spine = List.of(practice("review-ready-work", "Submitting review-ready work"));
        List<GoalStandingRow> rows = List.of(
            row(
                "review-ready-work",
                "Submitting review-ready work",
                Polarity.DESIRABLE,
                Observation.OBSERVED,
                Severity.INFO,
                2,
                1
            )
        );
        JsonNode root = build(spine, rows);

        JsonNode g = goal(root, "review-ready-work");
        assertThat(g.get("praiseChannelOpen").asBoolean()).isTrue();
        assertThat(g.get("affirmedCount").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("Polarity drives sign: an UNDESIRABLE practice counts OBSERVED as a problem")
    void polarityDrivesCounts() {
        List<Practice> spine = List.of(practice("anti-pattern", "Avoids anti-patterns"));
        List<GoalStandingRow> rows = List.of(
            row(
                "anti-pattern",
                "Avoids anti-patterns",
                Polarity.UNDESIRABLE,
                Observation.OBSERVED,
                Severity.MAJOR,
                4,
                2
            )
        );
        JsonNode root = build(spine, rows);

        JsonNode g = goal(root, "anti-pattern");
        assertThat(g.get("flaggedCount").asInt()).isEqualTo(4);
        assertThat(g.get("affirmedCount").asInt()).isZero();
        assertThat(g.get("praiseChannelOpen").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("priorities rank worst-severity-first and exclude BLIND goals")
    void prioritiesRankedWorstFirst() {
        List<Practice> spine = new ArrayList<>(
            List.of(
                practice("minor-goal", "Minor goal"),
                practice("critical-goal", "Critical goal"),
                practice("blind-goal", "Blind goal")
            )
        );
        List<GoalStandingRow> rows = List.of(
            row("minor-goal", "Minor goal", Polarity.DESIRABLE, Observation.NOT_OBSERVED, Severity.MINOR, 2, 0),
            row(
                "critical-goal",
                "Critical goal",
                Polarity.DESIRABLE,
                Observation.NOT_OBSERVED,
                Severity.CRITICAL,
                1,
                1
            ),
            row("blind-goal", "Blind goal", Polarity.DESIRABLE, Observation.NOT_APPLICABLE, Severity.INFO, 3, 0)
        );
        JsonNode root = build(spine, rows);

        JsonNode priorities = root.get("priorities");
        assertThat(priorities).hasSize(2);
        assertThat(priorities.get(0).get("goalSlug").asString()).isEqualTo("critical-goal");
        assertThat(priorities.get(0).get("topSeverity").asString()).isEqualTo("CRITICAL");
        assertThat(priorities.get(1).get("goalSlug").asString()).isEqualTo("minor-goal");
        for (JsonNode p : priorities) {
            assertThat(p.get("goalSlug").asString()).isNotEqualTo("blind-goal");
        }
    }

    @Test
    @DisplayName("worst severity is the most severe (CRITICAL beats MINOR), not enum-max")
    void worstSeverityIsMostSevere() {
        List<Practice> spine = List.of(practice("g", "Goal"));
        List<GoalStandingRow> rows = List.of(
            row("g", "Goal", Polarity.DESIRABLE, Observation.NOT_OBSERVED, Severity.MINOR, 1, 0),
            row("g", "Goal", Polarity.DESIRABLE, Observation.NOT_OBSERVED, Severity.CRITICAL, 1, 0)
        );
        JsonNode root = build(spine, rows);
        assertThat(goal(root, "g").get("topSeverity").asString()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("trajectory reflects the recent-vs-prior flag split")
    void trajectoryReflectsRecentVsPrior() {
        assertThat(trajectoryFor(5, 1)).isEqualTo("improving"); // prior 4 > recent 1
        assertThat(trajectoryFor(5, 4)).isEqualTo("regressing"); // recent 4 > prior 1
        assertThat(trajectoryFor(4, 2)).isEqualTo("steady"); // prior 2 == recent 2
    }

    @Test
    @DisplayName("contribute() writes practice_standing.json under the output key")
    void contributeWritesPracticeStandingJson() throws Exception {
        User user = new User();
        user.setLogin("student");
        when(userRepository.findById(eq(7L))).thenReturn(Optional.of(user));
        when(practiceRepository.findByWorkspaceIdAndActiveTrue(eq(1L))).thenReturn(
            List.of(practice("review-ready-work", "Submitting review-ready work"))
        );
        when(findingRepository.findGoalStandingByDeveloperAndWorkspace(eq(7L), eq(1L), any(), any())).thenReturn(
            List.of(
                row(
                    "review-ready-work",
                    "Submitting review-ready work",
                    Polarity.DESIRABLE,
                    Observation.OBSERVED,
                    Severity.INFO,
                    2,
                    1
                )
            )
        );

        Map<String, byte[]> files = new HashMap<>();
        provider.contribute(new ContextRequest.MentorChatRequest(1L, 7L, UUID.randomUUID()), files);

        byte[] bytes = files.get("inputs/context/practice_standing.json");
        assertThat(bytes).isNotNull();
        JsonNode root = objectMapper.readTree(bytes);
        assertThat(root.get("user").get("login").asString()).isEqualTo("student");
        assertThat(root.get("goals")).isNotEmpty();
    }

    @Test
    @DisplayName("no active practices and no findings yields empty goals and priorities")
    void emptyStandingIsEmpty() {
        JsonNode root = build(List.of(), List.of());
        assertThat(root.get("goals")).isEmpty();
        assertThat(root.get("priorities")).isEmpty();
    }

    private String trajectoryFor(long count, long recent) {
        JsonNode root = build(
            List.of(practice("g", "Goal")),
            List.of(row("g", "Goal", Polarity.DESIRABLE, Observation.NOT_OBSERVED, Severity.MAJOR, count, recent))
        );
        return goal(root, "g").get("trajectory").asString();
    }
}
