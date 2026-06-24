package de.tum.cit.aet.hephaestus.practices.finding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository.LocusFinding;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository.RunRef;
import de.tum.cit.aet.hephaestus.practices.finding.TrendDelta.LocusTransition;
import de.tum.cit.aet.hephaestus.practices.finding.TrendDelta.TransitionStatus;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/** Unit tests for the cross-run trend classifier (ADR 0021, A1). */
class FindingTrendServiceTest extends BaseUnitTest {

    @Mock
    private PracticeFindingRepository repo;

    @InjectMocks
    private FindingTrendService service;

    private static final UUID JOB_PREV = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID JOB_CURR = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final Long WS = 1L;
    private static final Long TARGET = 100L;

    private void stubTwoTargetRuns() {
        when(repo.findRecentRunRefsForTarget(eq(WorkArtifact.PULL_REQUEST), eq(TARGET), eq(WS), any())).thenReturn(
            List.of(
                runRef(JOB_CURR, Instant.parse("2026-06-15T10:00:00Z")),
                runRef(JOB_PREV, Instant.parse("2026-06-14T10:00:00Z"))
            )
        );
    }

    @Test
    void computeForTarget_classifiesNewPersistedResolved() {
        stubTwoTargetRuns();
        when(repo.findLociByAgentJobs(any(), eq(WS))).thenReturn(
            List.of(
                locus(JOB_PREV, "keyX", Presence.NOT_OBSERVED, Severity.MAJOR, "x", "X title"),
                locus(JOB_PREV, "keyY", Presence.OBSERVED, Severity.MINOR, "y", "Y title"),
                locus(JOB_CURR, "keyX", Presence.NOT_OBSERVED, Severity.MAJOR, "x", "X title v2"),
                locus(JOB_CURR, "keyZ", Presence.NOT_OBSERVED, Severity.CRITICAL, "z", "Z title")
            )
        );

        TrendDelta d = service.computeForTarget(WorkArtifact.PULL_REQUEST, TARGET, WS).orElseThrow();

        assertThat(d.countNew()).isEqualTo(1);
        assertThat(d.countResolved()).isEqualTo(1);
        assertThat(d.countPersisted()).isEqualTo(1);
        assertThat(d.countRegressed()).isZero();
        assertThat(d.hasMeaningfulChange()).isTrue();
        assertThat(status(d, "keyX")).isEqualTo(TransitionStatus.PERSISTED);
        assertThat(status(d, "keyY")).isEqualTo(TransitionStatus.RESOLVED);
        assertThat(status(d, "keyZ")).isEqualTo(TransitionStatus.NEW);
        // a RESOLVED locus carries the PRIOR run's prose (it is absent now; that is what the student last saw)
        LocusTransition resolved = transition(d, "keyY");
        assertThat(resolved.title()).isEqualTo("Y title");
        assertThat(resolved.priorObservation()).isEqualTo(Presence.OBSERVED);
        assertThat(resolved.currentObservation()).isNull();
    }

    @Test
    void computeForTarget_allLociPersistUnchanged_isNotMeaningful() {
        // The negative case that guards the silent A4 path: a re-review that only re-flags the same loci with
        // no observation change must NOT count as meaningful change (else it would ping the author about nothing).
        stubTwoTargetRuns();
        when(repo.findLociByAgentJobs(any(), eq(WS))).thenReturn(
            List.of(
                locus(JOB_PREV, "keyX", Presence.NOT_OBSERVED, Severity.MAJOR, "x", "still broken"),
                locus(JOB_CURR, "keyX", Presence.NOT_OBSERVED, Severity.MAJOR, "x", "still broken")
            )
        );

        TrendDelta d = service.computeForTarget(WorkArtifact.PULL_REQUEST, TARGET, WS).orElseThrow();

        assertThat(d.countPersisted()).isEqualTo(1);
        assertThat(d.countNew() + d.countResolved() + d.countRegressed()).isZero();
        assertThat(d.hasMeaningfulChange()).isFalse();
    }

    @Test
    void computeForTarget_ordersTransitionsRegressedNewResolvedPersisted() {
        stubTwoTargetRuns();
        when(repo.findLociByAgentJobs(any(), eq(WS))).thenReturn(
            List.of(
                locus(JOB_PREV, "kP", Presence.NOT_OBSERVED, Severity.MAJOR, "p", "persists"),
                locus(JOB_CURR, "kP", Presence.NOT_OBSERVED, Severity.MAJOR, "p", "persists"),
                locus(JOB_PREV, "kR", Presence.OBSERVED, Severity.MAJOR, "r", "was ok"),
                locus(JOB_CURR, "kR", Presence.NOT_OBSERVED, Severity.MAJOR, "r", "regressed"),
                locus(JOB_CURR, "kN", Presence.NOT_OBSERVED, Severity.MAJOR, "n", "new"),
                locus(JOB_PREV, "kS", Presence.NOT_OBSERVED, Severity.MAJOR, "s", "resolved")
            )
        );

        TrendDelta d = service.computeForTarget(WorkArtifact.PULL_REQUEST, TARGET, WS).orElseThrow();

        assertThat(d.transitions().stream().map(LocusTransition::status).toList()).containsExactly(
            TransitionStatus.REGRESSED,
            TransitionStatus.NEW,
            TransitionStatus.RESOLVED,
            TransitionStatus.PERSISTED
        );
    }

    private static LocusTransition transition(TrendDelta d, String key) {
        return d
            .transitions()
            .stream()
            .filter(t -> t.findingFingerprint().equals(key))
            .findFirst()
            .orElseThrow();
    }

    @Test
    void computeForTarget_observedThenNotObserved_isRegressed() {
        stubTwoTargetRuns();
        when(repo.findLociByAgentJobs(any(), eq(WS))).thenReturn(
            List.of(
                locus(JOB_PREV, "keyX", Presence.OBSERVED, Severity.MAJOR, "x", "satisfied last run"),
                locus(JOB_CURR, "keyX", Presence.NOT_OBSERVED, Severity.MAJOR, "x", "now broken")
            )
        );

        TrendDelta d = service.computeForTarget(WorkArtifact.PULL_REQUEST, TARGET, WS).orElseThrow();

        assertThat(d.countRegressed()).isEqualTo(1);
        LocusTransition t = d.transitions().get(0);
        assertThat(t.status()).isEqualTo(TransitionStatus.REGRESSED);
        assertThat(t.priorObservation()).isEqualTo(Presence.OBSERVED);
        assertThat(t.currentObservation()).isEqualTo(Presence.NOT_OBSERVED);
    }

    @Test
    void computeForTarget_notObservedThenObserved_isImprovementNotRegression() {
        stubTwoTargetRuns();
        when(repo.findLociByAgentJobs(any(), eq(WS))).thenReturn(
            List.of(
                locus(JOB_PREV, "keyX", Presence.NOT_OBSERVED, Severity.MAJOR, "x", "was broken"),
                locus(JOB_CURR, "keyX", Presence.OBSERVED, Severity.MAJOR, "x", "now satisfied")
            )
        );

        TrendDelta d = service.computeForTarget(WorkArtifact.PULL_REQUEST, TARGET, WS).orElseThrow();

        assertThat(d.countRegressed()).isZero();
        assertThat(d.countResolved()).isZero();
        LocusTransition t = d.transitions().get(0);
        assertThat(t.status()).isEqualTo(TransitionStatus.PERSISTED);
        assertThat(t.currentObservation()).isEqualTo(Presence.OBSERVED);
    }

    @Test
    void computeForTarget_duplicateLocusInOneRun_picksWorstSeverityRepresentative() {
        stubTwoTargetRuns();
        when(repo.findLociByAgentJobs(any(), eq(WS))).thenReturn(
            List.of(
                locus(JOB_PREV, "keyX", Presence.NOT_OBSERVED, Severity.MINOR, "x", "prior"),
                // current run emits keyX twice: MAJOR/conf .6 and CRITICAL/conf .9 → CRITICAL must win (severity over confidence)
                locusConf(JOB_CURR, "keyX", Presence.NOT_OBSERVED, Severity.MAJOR, 0.6f, "x", "dup a"),
                locusConf(JOB_CURR, "keyX", Presence.NOT_OBSERVED, Severity.CRITICAL, 0.9f, "x", "dup b")
            )
        );

        TrendDelta d = service.computeForTarget(WorkArtifact.PULL_REQUEST, TARGET, WS).orElseThrow();

        assertThat(d.transitions()).hasSize(1);
        assertThat(d.transitions().get(0).currentSeverity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void computeForTarget_singleRun_returnsEmpty() {
        when(repo.findRecentRunRefsForTarget(eq(WorkArtifact.PULL_REQUEST), eq(TARGET), eq(WS), any())).thenReturn(
            List.of(runRef(JOB_CURR, Instant.parse("2026-06-15T10:00:00Z")))
        );

        assertThat(service.computeForTarget(WorkArtifact.PULL_REQUEST, TARGET, WS)).isEmpty();
    }

    private static TransitionStatus status(TrendDelta d, String key) {
        return d
            .transitions()
            .stream()
            .filter(t -> t.findingFingerprint().equals(key))
            .findFirst()
            .orElseThrow()
            .status();
    }

    // --- projection stubs (anonymous implementations of the repository interface projections) ---

    private static RunRef runRef(UUID job, Instant at) {
        return new RunRef() {
            @Override
            public UUID getAgentJobId() {
                return job;
            }

            @Override
            public Instant getRunAt() {
                return at;
            }
        };
    }

    private static LocusFinding locus(UUID job, String key, Presence v, Severity sev, String slug, String title) {
        return locusConf(job, key, v, sev, 0.8f, slug, title);
    }

    private static LocusFinding locusConf(
        UUID job,
        String key,
        Presence v,
        Severity sev,
        float conf,
        String slug,
        String title
    ) {
        return new LocusFinding() {
            @Override
            public UUID getAgentJobId() {
                return job;
            }

            @Override
            public String getRecurrenceKey() {
                return key;
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
            public Float getConfidence() {
                return conf;
            }

            @Override
            public String getPracticeSlug() {
                return slug;
            }

            @Override
            public String getTitle() {
                return title;
            }

            @Override
            public Instant getObservedAt() {
                return Instant.parse("2026-06-15T10:00:00Z");
            }
        };
    }
}
