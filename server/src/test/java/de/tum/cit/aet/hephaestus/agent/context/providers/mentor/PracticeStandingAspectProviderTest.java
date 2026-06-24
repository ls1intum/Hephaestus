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
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository.AreaStandingRow;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeKind;
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

    private JsonNode build(List<Practice> spine, List<AreaStandingRow> rows) {
        User user = new User();
        user.setLogin("student");
        when(userRepository.findById(eq(7L))).thenReturn(Optional.of(user));
        when(practiceRepository.findByWorkspaceIdAndActiveTrue(eq(1L))).thenReturn(spine);
        when(findingRepository.findAreaStandingByDeveloperAndWorkspace(eq(7L), eq(1L), any(), any())).thenReturn(rows);
        return provider.buildPayload(1L, 7L);
    }

    private static Practice practice(String areaSlug, String areaName) {
        PracticeArea g = new PracticeArea();
        g.setSlug(areaSlug);
        g.setName(areaName);
        Practice p = new Practice();
        p.setArea(g);
        return p;
    }

    private static AreaStandingRow row(
        String slug,
        String name,
        PracticeKind pol,
        Presence v,
        Severity sev,
        long count,
        long recent
    ) {
        return new AreaStandingRow() {
            @Override
            public String getAreaSlug() {
                return slug;
            }

            @Override
            public String getAreaName() {
                return name;
            }

            @Override
            public PracticeKind getKind() {
                return pol;
            }

            @Override
            public Presence getObservation() {
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

    private static JsonNode area(JsonNode root, String slug) {
        for (JsonNode g : root.get("areas")) {
            if (slug.equals(g.get("areaSlug").asString())) {
                return g;
            }
        }
        throw new AssertionError("area not present: " + slug);
    }

    @Test
    @DisplayName("all-NA area is BLIND and excluded from priorities")
    void allNaAreaIsBlind() {
        List<Practice> spine = List.of(practice("constructive-code-review", "Reviewing constructively"));
        List<AreaStandingRow> rows = List.of(
            row(
                "constructive-code-review",
                "Reviewing constructively",
                PracticeKind.GOOD_PRACTICE,
                Presence.NOT_APPLICABLE,
                Severity.INFO,
                5,
                0
            )
        );
        JsonNode root = build(spine, rows);

        JsonNode g = area(root, "constructive-code-review");
        assertThat(g.get("assessmentState").asString()).isEqualTo("BLIND");
        assertThat(g.get("flaggedCount").asInt()).isZero();
        assertThat(root.get("priorities")).isEmpty();
    }

    @Test
    @DisplayName("flag-only GOOD_PRACTICE area has praiseChannelOpen=false (affirmation asymmetry guard)")
    void flagOnlyAreaHasPraiseChannelClosed() {
        List<Practice> spine = List.of(practice("robust-error-handling", "Handling failure robustly"));
        List<AreaStandingRow> rows = List.of(
            row(
                "robust-error-handling",
                "Handling failure robustly",
                PracticeKind.GOOD_PRACTICE,
                Presence.NOT_OBSERVED,
                Severity.MAJOR,
                3,
                1
            )
        );
        JsonNode root = build(spine, rows);

        JsonNode g = area(root, "robust-error-handling");
        assertThat(g.get("assessmentState").asString()).isEqualTo("ASSESSED");
        assertThat(g.get("praiseChannelOpen").asBoolean()).isFalse();
        assertThat(g.get("flaggedCount").asInt()).isEqualTo(3);
        assertThat(g.get("affirmedCount").asInt()).isZero();
    }

    @Test
    @DisplayName("an OBSERVED finding opens the praise channel")
    void affirmedAreaOpensPraiseChannel() {
        List<Practice> spine = List.of(practice("review-ready-work", "Submitting review-ready work"));
        List<AreaStandingRow> rows = List.of(
            row(
                "review-ready-work",
                "Submitting review-ready work",
                PracticeKind.GOOD_PRACTICE,
                Presence.OBSERVED,
                Severity.INFO,
                2,
                1
            )
        );
        JsonNode root = build(spine, rows);

        JsonNode g = area(root, "review-ready-work");
        assertThat(g.get("praiseChannelOpen").asBoolean()).isTrue();
        assertThat(g.get("affirmedCount").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("PracticeKind drives sign: an BAD_PRACTICE practice counts OBSERVED as a problem")
    void polarityDrivesCounts() {
        List<Practice> spine = List.of(practice("anti-pattern", "Avoids anti-patterns"));
        List<AreaStandingRow> rows = List.of(
            row(
                "anti-pattern",
                "Avoids anti-patterns",
                PracticeKind.BAD_PRACTICE,
                Presence.OBSERVED,
                Severity.MAJOR,
                4,
                2
            )
        );
        JsonNode root = build(spine, rows);

        JsonNode g = area(root, "anti-pattern");
        assertThat(g.get("flaggedCount").asInt()).isEqualTo(4);
        assertThat(g.get("affirmedCount").asInt()).isZero();
        assertThat(g.get("praiseChannelOpen").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("priorities rank worst-severity-first and exclude BLIND areas")
    void prioritiesRankedWorstFirst() {
        List<Practice> spine = new ArrayList<>(
            List.of(
                practice("minor-area", "Minor area"),
                practice("critical-area", "Critical area"),
                practice("blind-area", "Blind area")
            )
        );
        List<AreaStandingRow> rows = List.of(
            row("minor-area", "Minor area", PracticeKind.GOOD_PRACTICE, Presence.NOT_OBSERVED, Severity.MINOR, 2, 0),
            row(
                "critical-area",
                "Critical area",
                PracticeKind.GOOD_PRACTICE,
                Presence.NOT_OBSERVED,
                Severity.CRITICAL,
                1,
                1
            ),
            row("blind-area", "Blind area", PracticeKind.GOOD_PRACTICE, Presence.NOT_APPLICABLE, Severity.INFO, 3, 0)
        );
        JsonNode root = build(spine, rows);

        JsonNode priorities = root.get("priorities");
        assertThat(priorities).hasSize(2);
        assertThat(priorities.get(0).get("areaSlug").asString()).isEqualTo("critical-area");
        assertThat(priorities.get(0).get("topSeverity").asString()).isEqualTo("CRITICAL");
        assertThat(priorities.get(1).get("areaSlug").asString()).isEqualTo("minor-area");
        for (JsonNode p : priorities) {
            assertThat(p.get("areaSlug").asString()).isNotEqualTo("blind-area");
        }
    }

    @Test
    @DisplayName("worst severity is the most severe (CRITICAL beats MINOR), not enum-max")
    void worstSeverityIsMostSevere() {
        List<Practice> spine = List.of(practice("g", "Area"));
        List<AreaStandingRow> rows = List.of(
            row("g", "Area", PracticeKind.GOOD_PRACTICE, Presence.NOT_OBSERVED, Severity.MINOR, 1, 0),
            row("g", "Area", PracticeKind.GOOD_PRACTICE, Presence.NOT_OBSERVED, Severity.CRITICAL, 1, 0)
        );
        JsonNode root = build(spine, rows);
        assertThat(area(root, "g").get("topSeverity").asString()).isEqualTo("CRITICAL");
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
        when(findingRepository.findAreaStandingByDeveloperAndWorkspace(eq(7L), eq(1L), any(), any())).thenReturn(
            List.of(
                row(
                    "review-ready-work",
                    "Submitting review-ready work",
                    PracticeKind.GOOD_PRACTICE,
                    Presence.OBSERVED,
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
        assertThat(root.get("areas")).isNotEmpty();
    }

    @Test
    @DisplayName("no active practices and no findings yields empty areas and priorities")
    void emptyStandingIsEmpty() {
        JsonNode root = build(List.of(), List.of());
        assertThat(root.get("areas")).isEmpty();
        assertThat(root.get("priorities")).isEmpty();
    }

    private String trajectoryFor(long count, long recent) {
        JsonNode root = build(
            List.of(practice("g", "Area")),
            List.of(
                row("g", "Area", PracticeKind.GOOD_PRACTICE, Presence.NOT_OBSERVED, Severity.MAJOR, count, recent)
            )
        );
        return area(root, "g").get("trajectory").asString();
    }
}
