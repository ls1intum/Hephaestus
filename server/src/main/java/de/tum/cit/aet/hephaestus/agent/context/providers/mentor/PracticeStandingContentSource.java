package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.agent.context.ContentSource;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest.MentorChatRequest;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Materialises {@code inputs/context/practice_standing.json} for {@link MentorChatRequest}: a prepared
 * per-student standing brief so the mentor leads from the student's current standing instead of
 * re-deriving it from {@code findings_history.json} + {@code practice_catalog.json} each turn.
 *
 * <p>The guard fields ({@code assessmentState=BLIND}, {@code praiseChannelOpen}) are machine facts; how
 * the mentor must act on them is taught in {@code agent/mentor/system.md}.
 *
 * <p>Cache key: {@code workspaceId + ":" + developerId} — per-user-per-workspace.
 */
@Component
@RequiredArgsConstructor
public class PracticeStandingContentSource implements ContentSource {

    @Override
    public String originId() {
        return "core";
    }

    /** Workspace-relative output key. Whitelisted in {@code MentorContextKeys#ALLOWED_OUTPUT_KEYS}. */
    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "practice_standing.json";

    /** Look-back horizon — mirrors {@code ObservationHistoryContentSource}. */
    private static final int LOOKBACK_DAYS = 90;

    /** Recent-window split for the improving/regressing trajectory signal. */
    private static final int RECENT_DAYS = 14;

    /** Number of priority areas the mentor should lead with. */
    private static final int MAX_PRIORITIES = 3;

    // Upstream-quality floor (P4). A gap must clear these before it can set a MAJOR area-priority or be named
    // as a corroborated theme — otherwise a single low-confidence BAD on one artifact could become the
    // student's #1 "growth priority", nagging hardest about a habit they may have exemplified. For a
    // non-blocking formative system that is the trust-killer.

    /**
     * Distinct targets (artifact_id) a gap must appear on before it counts as a corroborated, MAJOR-eligible
     * priority. Below this it stays visible in {@code flaggedCount} but is held out of {@code priorities}
     * unless the practice's confidence is high (see {@link #CONFIDENT}). Two distinct artifacts is the
     * minimum at which "this is a habit, not a one-off" is defensible.
     */
    private static final int CORROBORATION_TARGETS = 2;

    /**
     * Confidence at/above which a single-target gap is still trusted enough to prioritise (the detector is
     * sure even if it only saw it once). Below {@link #QUARANTINE} a gap is too speculative to headline at
     * all. The 0.5 quarantine floor is the midpoint of the [0,1] confidence scale: a coin-flip hunch is not
     * a basis to nag a learner; 0.8 marks the detector's well-calibrated "confident" band (matching the
     * trend classifier's default-confidence fixtures).
     */
    private static final float CONFIDENT = 0.8f;

    /** Below this confidence a gap is quarantined: counted, but never a headline priority. */
    private static final float QUARANTINE = 0.5f;

    /**
     * Minimum flagged observations in the window before an area may be reported as {@code regressing}. A
     * trajectory verdict off one or two observations is noise, not a trend (P4: "never a verdict off ≤2
     * observations").
     */
    private static final int MIN_FLAGS_FOR_TREND = 3;

    private static final String CACHE_NAME = "mentor_practice_standing_context";

    private final UserRepository userRepository;
    private final ObservationRepository findingRepository;
    private final PracticeRepository practiceRepository;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    @Override
    public boolean supports(ContextRequest request) {
        return request instanceof MentorChatRequest;
    }

    @Override
    public boolean required() {
        return false;
    }

    /** Tx-on-contribute / not-on-buildPayload AOP convention documented at {@link MentorContextKeys}. */
    @Override
    @Transactional(readOnly = true)
    public void contribute(ContextRequest request, Map<String, byte[]> files) {
        MentorChatRequest req = (MentorChatRequest) request;
        String key = req.workspaceId() + ":" + req.developerId();
        Cache cache = cacheManager.getCache(CACHE_NAME);
        // Atomic compute-if-absent closes the get/build/put race on invalidation events.
        ObjectNode payload = (cache != null)
            ? cache.get(key, () -> buildPayload(req.workspaceId(), req.developerId()))
            : buildPayload(req.workspaceId(), req.developerId());
        try {
            files.put(OUTPUT_KEY, objectMapper.writeValueAsBytes(payload));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize practice standing context", e);
        }
    }

    /** Pure function of (workspaceId, developerId). Callers cache through {@link CacheManager}. */
    public ObjectNode buildPayload(Long workspaceId, Long developerId) {
        User user = userRepository
            .findById(developerId)
            .orElseThrow(() -> new EntityNotFoundException("User", developerId.toString()));
        Instant now = Instant.now();
        Instant since = now.minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        Instant recentSince = now.minus(RECENT_DAYS, ChronoUnit.DAYS);

        List<AreaStandingRow> rows = findingRepository.findAreaStandingByDeveloperAndWorkspace(
            developerId,
            workspaceId,
            since,
            recentSince
        );

        // Area spine: every area owning an ACTIVE practice — so an area with zero / all-NA findings
        // still appears (marked BLIND) instead of silently vanishing.
        Map<String, AreaAcc> areas = new LinkedHashMap<>();
        for (Practice p : practiceRepository.findByWorkspaceIdAndActiveTrue(workspaceId)) {
            PracticeArea g = p.getArea();
            if (g != null) {
                areas.computeIfAbsent(g.getSlug(), k -> new AreaAcc(k, g.getName()));
            }
        }

        for (AreaStandingRow r : rows) {
            AreaAcc a = areas.computeIfAbsent(r.getAreaSlug(), k -> new AreaAcc(k, r.getAreaName()));
            long count = r.getCount() == null ? 0 : r.getCount();
            long recent = r.getRecentCount() == null ? 0 : r.getRecentCount();
            long distinct = r.getDistinctTargets() == null ? 0 : r.getDistinctTargets();
            float conf = r.getMaxConfidence() == null ? 0f : r.getMaxConfidence();
            Presence v = r.getPresence();
            Assessment assessment = r.getAssessment();
            if (v == Presence.NOT_APPLICABLE) {
                a.naCount += count;
            } else if (assessment == Assessment.BAD) {
                a.flaggedCount += count;
                a.recentFlagged += recent;
                a.priorFlagged += (count - recent);
                // Corroboration/confidence MUST track the worst-severity flagged row ONLY — otherwise a
                // well-corroborated MINOR lends its distinct-target count / confidence to a single-target,
                // conf=0.3 CRITICAL, turning a coin-flip CRITICAL into the #1 priority and defeating the P4
                // quarantine floor. So: a strictly-worse severity row REPLACES the floor inputs (the new worst
                // carries its OWN corroboration), an equal-severity row maxes, a less-severe row is ignored.
                int rel = severityRelation(r.getSeverity(), a.topSeverity);
                if (rel > 0) {
                    // Strictly worse than the current worst — this row defines the new floor inputs.
                    a.flaggedDistinctTargets = distinct;
                    a.flaggedMaxConfidence = conf;
                } else if (rel == 0) {
                    // Same severity as the current worst — corroborate within the worst tier.
                    a.flaggedDistinctTargets = Math.max(a.flaggedDistinctTargets, distinct);
                    a.flaggedMaxConfidence = Math.max(a.flaggedMaxConfidence, conf);
                }
                // rel < 0: less severe than the current worst — counts toward flaggedCount but never the floor.
                a.topSeverity = worst(a.topSeverity, r.getSeverity());
            } else if (assessment == Assessment.GOOD) {
                a.affirmedCount += count;
                a.affirmedDistinctTargets = Math.max(a.affirmedDistinctTargets, distinct);
            }
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.putObject("user").put("login", user.getLogin()).put("name", user.getName());
        ObjectNode window = root.putObject("window");
        window.put("lookbackDays", LOOKBACK_DAYS).put("recentDays", RECENT_DAYS).put("generatedAt", now.toString());

        ArrayNode areasArr = root.putArray("areas");
        List<AreaAcc> priorityCandidates = new ArrayList<>();
        for (AreaAcc a : areas.values()) {
            boolean assessed = (a.flaggedCount + a.affirmedCount) > 0;
            // A gap clears the upstream-quality floor (P4) when it is corroborated across enough distinct
            // targets, OR the detector is confident about the single sighting. Below the quarantine floor it
            // is too speculative to act on at all. This is the gate for "is this a real theme to coach?".
            boolean gapCorroborated = a.flaggedCount > 0 && corroborated(a);
            // An ASSESSED-but-uncorroborated area (only quarantined/single-target gaps, no affirmations) is
            // NOT_MEASURED, not a gap: the work didn't surface enough signal to judge it. Distinct from BLIND
            // (the area cannot be exercised by this kind of work at all). Matches mentor system.md.
            String assessmentState = !assessed
                ? "BLIND"
                : (gapCorroborated || a.affirmedCount > 0)
                    ? "ASSESSED"
                    : "NOT_MEASURED";

            ObjectNode g = areasArr.addObject();
            g.put("areaSlug", a.slug);
            g.put("areaName", a.name);
            g.put("assessmentState", assessmentState);
            g.put("flaggedCount", a.flaggedCount);
            g.put("affirmedCount", a.affirmedCount);
            g.put("naCount", a.naCount);
            g.put("praiseChannelOpen", a.affirmedCount > 0);
            if (a.topSeverity != null) {
                g.put("topSeverity", a.topSeverity.name());
            }
            g.put("recentFlagged", a.recentFlagged);
            g.put("trajectory", trajectory(a));
            // Only a corroborated gap may set an area priority — a single low-confidence BAD stays counted
            // but never becomes a headline the mentor steers toward.
            if (gapCorroborated) {
                priorityCandidates.add(a);
            }
        }

        // Worst-first by severity (ordinal CRITICAL=0..INFO=3, so ASCENDING ordinal = MORE severe —
        // NOT a max), then by recent activity, then by total flagged.
        priorityCandidates.sort(
            Comparator.<AreaAcc>comparingInt(a -> a.topSeverity == null ? Integer.MAX_VALUE : a.topSeverity.ordinal())
                .thenComparing(Comparator.<AreaAcc>comparingLong(a -> a.recentFlagged).reversed())
                .thenComparing(Comparator.<AreaAcc>comparingLong(a -> a.flaggedCount).reversed())
        );
        ArrayNode prioritiesArr = root.putArray("priorities");
        for (AreaAcc a : priorityCandidates.subList(0, Math.min(MAX_PRIORITIES, priorityCandidates.size()))) {
            ObjectNode n = prioritiesArr.addObject();
            n.put("areaSlug", a.slug);
            n.put("areaName", a.name);
            if (a.topSeverity != null) {
                n.put("topSeverity", a.topSeverity.name());
            }
            n.put("flaggedCount", a.flaggedCount);
            n.put("recentFlagged", a.recentFlagged);
        }

        // P6: one synthesized cross-artifact theme — the one durable corroborated strength and the one
        // durable corroborated gap (each spanning the most distinct targets), single-artifact/low-confidence
        // findings excluded. Turns the sorted area list into the longitudinal headline a standing view is for.
        writeHeadline(root, areas.values(), priorityCandidates);
        return root;
    }

    /**
     * A flagged area clears the corroboration/quarantine floor (P4) when its worst gap appears on
     * {@link #CORROBORATION_TARGETS}+ distinct targets, OR the detector is {@link #CONFIDENT} about it on a
     * single target. A gap below the {@link #QUARANTINE} confidence floor never clears, regardless of count.
     */
    private static boolean corroborated(AreaAcc a) {
        if (a.flaggedMaxConfidence < QUARANTINE) {
            return false;
        }
        return a.flaggedDistinctTargets >= CORROBORATION_TARGETS || a.flaggedMaxConfidence >= CONFIDENT;
    }

    /**
     * Synthesizes the {@code headline} object: the most-corroborated durable strength and gap, each excluding
     * single-artifact / low-confidence findings, so the mentor can name the cross-artifact theme rather than
     * just the top of a sorted checklist. Either side may be absent (null) when nothing durable qualifies.
     */
    private static void writeHeadline(ObjectNode root, Iterable<AreaAcc> areas, List<AreaAcc> corroboratedGaps) {
        AreaAcc strength = null;
        for (AreaAcc a : areas) {
            // A durable strength: affirmed across the MOST distinct targets (≥ corroboration threshold).
            if (a.affirmedCount > 0 && a.affirmedDistinctTargets >= CORROBORATION_TARGETS) {
                if (strength == null || a.affirmedDistinctTargets > strength.affirmedDistinctTargets) {
                    strength = a;
                }
            }
        }
        // The durable gap: most distinct targets among the already-corroborated gaps (worst severity breaks ties).
        AreaAcc gap = corroboratedGaps
            .stream()
            .max(
                Comparator.<AreaAcc>comparingLong(a -> a.flaggedDistinctTargets).thenComparingInt(a ->
                    a.topSeverity == null ? -1 : (Severity.values().length - a.topSeverity.ordinal())
                )
            )
            .orElse(null);

        ObjectNode headline = root.putObject("headline");
        if (strength != null) {
            headline.putObject("durableStrength").put("areaSlug", strength.slug).put("areaName", strength.name);
        } else {
            headline.putNull("durableStrength");
        }
        if (gap != null) {
            ObjectNode g = headline.putObject("durableGap").put("areaSlug", gap.slug).put("areaName", gap.name);
            if (gap.topSeverity != null) {
                g.put("topSeverity", gap.topSeverity.name());
            }
        } else {
            headline.putNull("durableGap");
        }
    }

    /**
     * Severity relation of {@code candidate} against the current worst {@code current}, for the floor-input fold:
     * {@code >0} when candidate is strictly worse (so it REPLACES the floor inputs), {@code 0} when equally severe
     * (max within the tier), {@code <0} when less severe (ignored for the floor). Lower ordinal = more severe.
     * A null current means no worst-severity row yet, so any candidate (incl. null) seeds the floor (returns 1).
     */
    private static int severityRelation(@Nullable Severity candidate, @Nullable Severity current) {
        if (current == null) {
            return 1; // first flagged row of the area defines the floor inputs
        }
        if (candidate == null) {
            return -1; // unknown severity never displaces a known worst tier
        }
        // ordinal ascending = more severe; candidate strictly worse ⇒ strictly smaller ordinal.
        return Integer.compare(current.ordinal(), candidate.ordinal());
    }

    @Nullable
    private static Severity worst(@Nullable Severity current, @Nullable Severity candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null) {
            return current;
        }
        return candidate.ordinal() < current.ordinal() ? candidate : current;
    }

    private static String trajectory(AreaAcc a) {
        if (a.flaggedCount == 0) {
            return "none";
        }
        // Trend safety (P4): never broadcast "regressing" off too few observations — a verdict on ≤2 flags is
        // noise, not a trend, and a false "you're getting worse" is corrosive in a formative system. Below the
        // floor we report "steady" (honest: not enough to claim a direction). Easing (improving) is safe to
        // surface at any count — it never nags.
        if (a.recentFlagged > a.priorFlagged) {
            return a.flaggedCount >= MIN_FLAGS_FOR_TREND ? "regressing" : "steady";
        }
        if (a.recentFlagged < a.priorFlagged) {
            return "improving";
        }
        return "steady";
    }

    private static final class AreaAcc {

        final String slug;
        final String name;
        long flaggedCount;
        long affirmedCount;
        long naCount;
        long recentFlagged;
        long priorFlagged;

        /**
         * Distinct targets the WORST-severity flagged signal spans, and its strongest confidence — the P4 floor
         * inputs. Tracked for the worst tier ONLY (replaced on a strictly-worse row, maxed within the tier) so a
         * well-corroborated lower-severity gap cannot lend its corroboration to a weaker, more-severe one.
         */
        long flaggedDistinctTargets;
        float flaggedMaxConfidence;

        /** Distinct targets the affirmations span — the durable-strength signal for the P6 headline. */
        long affirmedDistinctTargets;

        @Nullable
        Severity topSeverity;

        AreaAcc(String slug, String name) {
            this.slug = slug;
            this.name = name;
        }
    }
}
