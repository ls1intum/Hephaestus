package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.agent.context.ContentProvider;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest.MentorChatRequest;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository.AreaStandingRow;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
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
public class PracticeStandingAspectProvider implements ContentProvider {

    @Override
    public String connectorId() {
        return "core";
    }

    /** Workspace-relative output key. Whitelisted in {@code MentorAspects#ALLOWED_OUTPUT_KEYS}. */
    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "practice_standing.json";

    /** Look-back horizon — mirrors {@code FindingsHistoryAspectProvider}. */
    private static final int LOOKBACK_DAYS = 90;

    /** Recent-window split for the improving/regressing trajectory signal. */
    private static final int RECENT_DAYS = 14;

    /** Number of priority areas the mentor should lead with. */
    private static final int MAX_PRIORITIES = 3;

    private static final String CACHE_NAME = "mentor_practice_standing_aspect";

    private final UserRepository userRepository;
    private final PracticeFindingRepository findingRepository;
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

    /** Tx-on-contribute / not-on-buildPayload AOP convention documented at {@link MentorAspects}. */
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
            throw new IllegalStateException("Failed to serialize practice standing aspect", e);
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
            Presence v = r.getPresence();
            Assessment assessment = r.getAssessment();
            if (v == Presence.NOT_APPLICABLE) {
                a.naCount += count;
            } else if (assessment == Assessment.BAD) {
                a.flaggedCount += count;
                a.recentFlagged += recent;
                a.priorFlagged += (count - recent);
                a.topSeverity = worst(a.topSeverity, r.getSeverity());
            } else if (assessment == Assessment.GOOD) {
                a.affirmedCount += count;
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
            ObjectNode g = areasArr.addObject();
            g.put("areaSlug", a.slug);
            g.put("areaName", a.name);
            g.put("assessmentState", assessed ? "ASSESSED" : "BLIND");
            g.put("flaggedCount", a.flaggedCount);
            g.put("affirmedCount", a.affirmedCount);
            g.put("naCount", a.naCount);
            g.put("praiseChannelOpen", a.affirmedCount > 0);
            if (a.topSeverity != null) {
                g.put("topSeverity", a.topSeverity.name());
            }
            g.put("recentFlagged", a.recentFlagged);
            g.put("trajectory", trajectory(a));
            if (assessed && a.flaggedCount > 0) {
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
        return root;
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
        if (a.recentFlagged > a.priorFlagged) {
            return "regressing";
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

        @Nullable
        Severity topSeverity;

        AreaAcc(String slug, String name) {
            this.slug = slug;
            this.name = name;
        }
    }
}
