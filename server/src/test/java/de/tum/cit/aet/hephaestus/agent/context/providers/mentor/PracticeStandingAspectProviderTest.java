package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.AreaStandingRow;
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
    ObservationRepository findingRepository;

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

    /**
     * 7-arg row: corroborated by construction (2 distinct targets, high confidence) so the floor-agnostic
     * assertions pass. Floor tests use the 9-arg overload to set the P4 inputs explicitly.
     */
    private static AreaStandingRow row(
        String slug,
        String name,
        Presence v,
        Assessment assessment,
        Severity sev,
        long count,
        long recent
    ) {
        return row(slug, name, v, assessment, sev, count, recent, 2L, 0.9f);
    }

    private static AreaStandingRow row(
        String slug,
        String name,
        Presence v,
        Assessment assessment,
        Severity sev,
        long count,
        long recent,
        long distinctTargets,
        float maxConfidence
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
            public Presence getPresence() {
                return v;
            }

            @Override
            public Assessment getAssessment() {
                return assessment;
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

            @Override
            public Long getDistinctTargets() {
                return distinctTargets;
            }

            @Override
            public Float getMaxConfidence() {
                return maxConfidence;
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
            // (NOT_APPLICABLE, null): the model nulls severity for an NA observation.
            row("constructive-code-review", "Reviewing constructively", Presence.NOT_APPLICABLE, null, null, 5, 0)
        );
        JsonNode root = build(spine, rows);

        JsonNode g = area(root, "constructive-code-review");
        assertThat(g.get("assessmentState").asString()).isEqualTo("BLIND");
        assertThat(g.get("flaggedCount").asInt()).isZero();
        assertThat(g.get("naCount").asInt()).isEqualTo(5);
        assertThat(root.get("priorities")).isEmpty();
    }

    @Test
    @DisplayName("an (ABSENT, GOOD) clean observation still opens the praise channel")
    void absenceIsGoodOpensPraiseChannel() {
        List<Practice> spine = List.of(practice("secure-by-default-changes", "Keeping changes secure by default"));
        List<AreaStandingRow> rows = List.of(
            // (ABSENT, GOOD) [a clean baseline: a bad behaviour that could have appeared was avoided]
            row(
                "secure-by-default-changes",
                "Keeping changes secure by default",
                Presence.ABSENT,
                Assessment.GOOD,
                null,
                2,
                1
            )
        );
        JsonNode root = build(spine, rows);

        JsonNode g = area(root, "secure-by-default-changes");
        assertThat(g.get("praiseChannelOpen").asBoolean()).isTrue();
        assertThat(g.get("affirmedCount").asInt()).isEqualTo(2);
        assertThat(g.get("flaggedCount").asInt()).isZero();
    }

    @Test
    @DisplayName("flag-only area has praiseChannelOpen=false (affirmation asymmetry guard)")
    void flagOnlyAreaHasPraiseChannelClosed() {
        List<Practice> spine = List.of(practice("robust-error-handling", "Handling failure robustly"));
        List<AreaStandingRow> rows = List.of(
            // (ABSENT, BAD) [a gap]
            row(
                "robust-error-handling",
                "Handling failure robustly",
                Presence.ABSENT,
                Assessment.BAD,
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
    @DisplayName("a (PRESENT, GOOD) observation opens the praise channel")
    void affirmedAreaOpensPraiseChannel() {
        List<Practice> spine = List.of(practice("review-ready-work", "Submitting review-ready work"));
        List<AreaStandingRow> rows = List.of(
            // (PRESENT, GOOD) [a strength]
            row(
                "review-ready-work",
                "Submitting review-ready work",
                Presence.PRESENT,
                Assessment.GOOD,
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
    @DisplayName("assessment drives sign: a BAD-assessment observation counts as a problem")
    void polarityDrivesCounts() {
        List<Practice> spine = List.of(practice("anti-pattern", "Avoids anti-patterns"));
        List<AreaStandingRow> rows = List.of(
            // (PRESENT, BAD) [a problem]
            row("anti-pattern", "Avoids anti-patterns", Presence.PRESENT, Assessment.BAD, Severity.MAJOR, 4, 2)
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
            row("minor-area", "Minor area", Presence.ABSENT, Assessment.BAD, Severity.MINOR, 2, 0),
            row("critical-area", "Critical area", Presence.ABSENT, Assessment.BAD, Severity.CRITICAL, 1, 1),
            row("blind-area", "Blind area", Presence.NOT_APPLICABLE, null, Severity.INFO, 3, 0)
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
            row("g", "Area", Presence.ABSENT, Assessment.BAD, Severity.MINOR, 1, 0),
            row("g", "Area", Presence.ABSENT, Assessment.BAD, Severity.CRITICAL, 1, 0)
        );
        JsonNode root = build(spine, rows);
        assertThat(area(root, "g").get("topSeverity").asString()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("A4: a well-corroborated MINOR does NOT lend corroboration to a weak CRITICAL in the same area")
    void crossSeverityCorroborationDoesNotBleedToWorseSeverity() {
        // Single area with two BAD rows: a well-corroborated MINOR (3 targets, conf 0.9 — clears the floor on
        // its own) and a weak CRITICAL (1 target, conf 0.3 — below the QUARANTINE floor). The worst-severity
        // floor inputs MUST track the CRITICAL row, so the area is uncorroborated and NEVER headlines at
        // CRITICAL. A global Math.max would bleed the MINOR's 3 targets / 0.9 conf onto the CRITICAL,
        // surfacing a coin-flip CRITICAL as the #1 priority and defeating the P4 quarantine floor.
        List<Practice> spine = List.of(practice("mixed-area", "Mixed severity area"));
        List<AreaStandingRow> rows = List.of(
            // well-corroborated MINOR: distinct=3, conf=0.9
            row("mixed-area", "Mixed severity area", Presence.ABSENT, Assessment.BAD, Severity.MINOR, 4, 1, 3L, 0.9f),
            // weak CRITICAL: distinct=1, conf=0.3 (below QUARANTINE)
            row("mixed-area", "Mixed severity area", Presence.ABSENT, Assessment.BAD, Severity.CRITICAL, 1, 1, 1L, 0.3f)
        );
        JsonNode root = build(spine, rows);

        // topSeverity is still CRITICAL (the worst seen), but the area is NOT a priority — the weak CRITICAL
        // doesn't clear the floor and the MINOR's corroboration can no longer rescue it.
        assertThat(area(root, "mixed-area").get("topSeverity").asString()).isEqualTo("CRITICAL");
        assertThat(area(root, "mixed-area").get("assessmentState").asString()).isEqualTo("NOT_MEASURED");
        assertThat(root.get("priorities")).isEmpty();
        assertThat(root.get("headline").get("durableGap").isNull()).isTrue();
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
                    Presence.PRESENT,
                    Assessment.GOOD,
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
            List.of(row("g", "Area", Presence.ABSENT, Assessment.BAD, Severity.MAJOR, count, recent))
        );
        return area(root, "g").get("trajectory").asString();
    }

    // --- P4/P6 upstream-quality floor ---

    @Test
    @DisplayName("a single low-confidence BAD does NOT set an area priority and reads NOT_MEASURED")
    void singleLowConfidenceBadIsQuarantined() {
        List<Practice> spine = List.of(practice("robust-error-handling", "Handling failure robustly"));
        // one MAJOR gap, one target, confidence 0.4 (below the 0.5 quarantine floor)
        List<AreaStandingRow> rows = List.of(
            row(
                "robust-error-handling",
                "Handling failure robustly",
                Presence.ABSENT,
                Assessment.BAD,
                Severity.MAJOR,
                1,
                1,
                1L,
                0.4f
            )
        );
        JsonNode root = build(spine, rows);

        JsonNode g = area(root, "robust-error-handling");
        assertThat(g.get("assessmentState").asString()).isEqualTo("NOT_MEASURED");
        assertThat(g.get("flaggedCount").asInt()).isEqualTo(1); // still visible, just not a headline
        assertThat(root.get("priorities")).isEmpty();
    }

    @Test
    @DisplayName("a corroborated BAD (>=2 distinct targets) DOES set an area priority and reads ASSESSED")
    void corroboratedBadIsPrioritised() {
        List<Practice> spine = List.of(practice("robust-error-handling", "Handling failure robustly"));
        // same MAJOR gap seen on TWO distinct targets, modest confidence (above quarantine, below CONFIDENT)
        // — corroboration across targets carries it past the floor where confidence alone would not.
        List<AreaStandingRow> rows = List.of(
            row(
                "robust-error-handling",
                "Handling failure robustly",
                Presence.ABSENT,
                Assessment.BAD,
                Severity.MAJOR,
                2,
                1,
                2L,
                0.6f
            )
        );
        JsonNode root = build(spine, rows);

        JsonNode g = area(root, "robust-error-handling");
        assertThat(g.get("assessmentState").asString()).isEqualTo("ASSESSED");
        assertThat(root.get("priorities")).hasSize(1);
        assertThat(root.get("priorities").get(0).get("areaSlug").asString()).isEqualTo("robust-error-handling");
    }

    @Test
    @DisplayName("a confident single-target BAD still prioritises (confidence substitutes for corroboration)")
    void confidentSingleTargetBadIsPrioritised() {
        List<Practice> spine = List.of(practice("robust-error-handling", "Handling failure robustly"));
        List<AreaStandingRow> rows = List.of(
            row(
                "robust-error-handling",
                "Handling failure robustly",
                Presence.ABSENT,
                Assessment.BAD,
                Severity.MAJOR,
                1,
                1,
                1L,
                0.95f
            )
        );
        JsonNode root = build(spine, rows);

        assertThat(area(root, "robust-error-handling").get("assessmentState").asString()).isEqualTo("ASSESSED");
        assertThat(root.get("priorities")).hasSize(1);
    }

    @Test
    @DisplayName("regressing requires >=3 flagged observations; below the floor it reads steady")
    void regressingNeedsThreeFlags() {
        // recent (2) > prior (0) but only 2 flagged total → too few to claim a direction
        JsonNode below = build(
            List.of(practice("g", "Area")),
            List.of(row("g", "Area", Presence.ABSENT, Assessment.BAD, Severity.MAJOR, 2, 2, 2L, 0.9f))
        );
        assertThat(area(below, "g").get("trajectory").asString()).isEqualTo("steady");

        // recent (3) > prior (0), 3 flagged → regressing is allowed
        JsonNode at = build(
            List.of(practice("g", "Area")),
            List.of(row("g", "Area", Presence.ABSENT, Assessment.BAD, Severity.MAJOR, 3, 3, 2L, 0.9f))
        );
        assertThat(area(at, "g").get("trajectory").asString()).isEqualTo("regressing");
    }

    @Test
    @DisplayName("headline names the durable corroborated strength and gap, excluding single-target noise")
    void headlineNamesDurableStrengthAndGap() {
        List<Practice> spine = new ArrayList<>(
            List.of(
                practice("review-ready-work", "Submitting review-ready work"),
                practice("robust-error-handling", "Handling failure robustly"),
                practice("noise-area", "Noisy single-target gap")
            )
        );
        List<AreaStandingRow> rows = List.of(
            // durable strength: affirmed across 3 distinct targets
            row(
                "review-ready-work",
                "Submitting review-ready work",
                Presence.PRESENT,
                Assessment.GOOD,
                null,
                3,
                1,
                3L,
                0.9f
            ),
            // durable gap: flagged across 2 distinct targets
            row(
                "robust-error-handling",
                "Handling failure robustly",
                Presence.ABSENT,
                Assessment.BAD,
                Severity.MAJOR,
                2,
                1,
                2L,
                0.9f
            ),
            // single-target low-confidence gap: must NOT win the headline
            row(
                "noise-area",
                "Noisy single-target gap",
                Presence.ABSENT,
                Assessment.BAD,
                Severity.CRITICAL,
                1,
                1,
                1L,
                0.3f
            )
        );
        JsonNode root = build(spine, rows);

        JsonNode headline = root.get("headline");
        assertThat(headline.get("durableStrength").get("areaSlug").asString()).isEqualTo("review-ready-work");
        assertThat(headline.get("durableGap").get("areaSlug").asString()).isEqualTo("robust-error-handling");
    }

    @Test
    @DisplayName("headline gap is null when no gap is corroborated")
    void headlineGapNullWhenNoneCorroborated() {
        List<Practice> spine = List.of(practice("noise-area", "Noisy single-target gap"));
        List<AreaStandingRow> rows = List.of(
            row(
                "noise-area",
                "Noisy single-target gap",
                Presence.ABSENT,
                Assessment.BAD,
                Severity.CRITICAL,
                1,
                1,
                1L,
                0.3f
            )
        );
        JsonNode root = build(spine, rows);
        assertThat(root.get("headline").get("durableGap").isNull()).isTrue();
        assertThat(root.get("headline").get("durableStrength").isNull()).isTrue();
    }
}
