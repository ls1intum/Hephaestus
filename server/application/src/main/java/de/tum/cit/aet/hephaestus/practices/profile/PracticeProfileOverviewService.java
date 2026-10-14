package de.tum.cit.aet.hephaestus.practices.profile;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.PracticeGroupService;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackDecisionRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.page.FeedbackClosure;
import de.tum.cit.aet.hephaestus.practices.feedback.page.PracticePageFeedbackBasis;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGroup;
import de.tum.cit.aet.hephaestus.practices.profile.dto.OverviewWindowDTO;
import de.tum.cit.aet.hephaestus.practices.profile.dto.PracticeProfileOverviewDTO;
import de.tum.cit.aet.hephaestus.practices.profile.dto.ProfileChangeDTO;
import de.tum.cit.aet.hephaestus.practices.profile.dto.ProfileReviewRefDTO;
import de.tum.cit.aet.hephaestus.practices.result.GoingWellQueryService;
import de.tum.cit.aet.hephaestus.practices.result.PracticeGroupStandingService;
import de.tum.cit.aet.hephaestus.practices.result.PracticeStandingService;
import de.tum.cit.aet.hephaestus.practices.result.ResultReadQuery;
import de.tum.cit.aet.hephaestus.practices.result.ResultView;
import de.tum.cit.aet.hephaestus.practices.result.ResultViewQuery;
import de.tum.cit.aet.hephaestus.practices.result.ResultVisibilityPolicy;
import de.tum.cit.aet.hephaestus.practices.result.trend.WorkResolution;
import de.tum.cit.aet.hephaestus.practices.result.trend.WorkResolution.Work;
import de.tum.cit.aet.hephaestus.practices.review.ProductReferenceQuery;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDecisionAction;
import de.tum.cit.aet.hephaestus.practices.spi.ProfileChangeResolvedBy;
import de.tum.cit.aet.hephaestus.practices.spi.ProfileChangeType;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PracticeProfileOverviewService {
    private final CurrentDeveloperLookup currentDeveloperLookup;
    private final PracticeStandingService practiceStandingService;
    private final PracticeGroupStandingService practiceGroupStandingService;
    private final PracticeGroupService practiceGroupService;
    private final ResultReadQuery read;
    private final ResultViewQuery views;
    private final ResultVisibilityPolicy visibility;
    private final FeedbackRepository feedbackRepository;
    private final FeedbackDecisionRepository decisions;
    private final PracticePageFeedbackBasis feedbackEvidence;
    private final ProductReferenceQuery references;
    private final JdbcClient jdbc;
    private final GoingWellQueryService goingWell;
    private final Clock clock;

    public PracticeProfileOverviewDTO getOverview(WorkspaceContext ws) {
        var now = clock.instant();
        var account = currentDeveloperLookup.currentDeveloperId();
        if (account.isEmpty()) {
            var empty = OverviewWindow.sincePreviousRun(null, now);
            return new PracticeProfileOverviewDTO(
                    new OverviewWindowDTO(empty.since(), empty.until()), null, List.of(), List.of(), List.of());
        }
        var rows = read.forDevelopersSince(ws.id(), List.of(account.get()), now.minus(OverviewWindow.LOOKBACK), false);
        var permitted = visibility.permitsHistory(ws.id(), rows, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY);
        var projected = views.views(
                ws.id(),
                rows.stream().filter(r -> permitted.contains(r.getId())).toList());
        var newest = new LinkedHashMap<UUID, ResultView>();
        projected.forEach(v -> newest.putIfAbsent(v.result().getReviewId(), v));
        var newestReviews = List.copyOf(newest.values());
        var latest = newestReviews.isEmpty() ? null : newestReviews.getFirst();
        var window = OverviewWindow.sincePreviousRun(
                newestReviews.size() < 2 ? null : newestReviews.get(1).result().getRecordedAt(), now);
        var snapshots = practiceStandingService.getStandingSnapshots(
                ws.id(), account.get(), List.of(window.since(), window.until()));
        var before = snapshots.getFirst();
        var after = snapshots.getLast();
        var groups = practiceGroupService.listGroups(ws, true);
        Map<String, List<Work>> windowWork = new LinkedHashMap<>();
        projected.stream()
                .filter(v -> window.contains(v.result().getRecordedAt()))
                .collect(Collectors.groupingBy(v -> v.practice().getSlug()))
                .forEach((slug, v) ->
                        windowWork.put(slug, Work.newestFirst(v.stream().map(Work::of))));
        var first = jdbc
                .sql("SELECT p.slug,min(r.recorded_at) FROM result r JOIN practice p ON p.id=r.practice_id AND"
                        + " p.workspace_id=r.workspace_id WHERE r.workspace_id=:ws AND r.developer_id=:account"
                        + " GROUP BY p.slug")
                .param("ws", ws.id())
                .param("account", account.get())
                .query((rs, n) -> Map.entry(
                        rs.getString(1), rs.getObject(2, OffsetDateTime.class).toInstant()))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        var feedback = readFeedback(ws.id(), account.get(), now, window);
        var cited = new LinkedHashSet<Long>();
        projected.forEach(v -> cited.add(v.result().getItemId()));
        feedback.evidenceByFeedback()
                .values()
                .forEach(v -> v.forEach(r -> cited.add(r.result().getItemId())));
        Stream.concat(
                        feedback.resolutionByFeedback().values().stream(),
                        feedback.resolutionBeforeByFeedback().values().stream())
                .forEach(r -> {
                    r.cleanWork().forEach(w -> cited.add(w.itemId()));
                    r.problemWork().forEach(w -> cited.add(w.itemId()));
                });
        var items = references.items(ws.id(), cited);
        var changes = new ArrayList<>(ProfileChangeDetector.detect(
                window,
                before.dtos(),
                after.dtos(),
                practiceGroupStandingService.summarize(groups, before),
                practiceGroupStandingService.summarize(groups, after),
                windowWork,
                first,
                items));
        changes.addAll(feedbackChanges(feedback, window, items));
        changes.removeIf(c -> c.practiceSlug() != null && !after.practices().containsKey(c.practiceSlug()));
        changes.sort(Comparator.comparing(ProfileChangeDTO::at).reversed());
        var seen = new HashSet<String>();
        var unique = changes.stream()
                .filter(c -> c.feedbackId() == null || seen.add(c.practiceSlug() + ":" + c.type()))
                .toList();
        var reviewed = projected.stream()
                .filter(v -> window.contains(v.result().getRecordedAt()))
                .map(ResultView::item)
                .distinct()
                .toList();
        return new PracticeProfileOverviewDTO(
                new OverviewWindowDTO(window.since(), window.until()),
                latest == null
                        ? null
                        : new ProfileReviewRefDTO(
                                latest.review().getId(),
                                latest.result().getRecordedAt(),
                                latest.item(),
                                latest.review().getStatus()),
                goingWell.own(ws.id()),
                unique,
                reviewed);
    }

    private record FeedbackFacts(
            List<Feedback> recent,
            Map<UUID, Instant> reviewedAtByRun,
            Map<UUID, List<ResultView>> evidenceByFeedback,
            Map<UUID, WorkResolution> resolutionByFeedback,
            Map<UUID, WorkResolution> resolutionBeforeByFeedback,
            Map<UUID, Instant> addressedAt,
            Map<UUID, Instant> practiceChangedAt) {}

    private FeedbackFacts readFeedback(long ws, long account, Instant now, OverviewWindow window) {
        var readable = feedbackRepository.findReadablePracticePage(ws, List.of(account)).stream()
                .filter(f -> !f.getCreatedAt().isBefore(now.minus(OverviewWindow.LOOKBACK)))
                .toList();
        var withdrawn = readable.isEmpty()
                ? Set.<UUID>of()
                : decisions
                        .findCurrentVisibilityForFeedbackIds(
                                ws, readable.stream().map(Feedback::getId).toList())
                        .stream()
                        .filter(d -> d.getAction() == FeedbackDecisionAction.WITHDRAW)
                        .map(d -> d.getFeedbackId())
                        .collect(Collectors.toSet());
        var recent =
                readable.stream().filter(f -> !withdrawn.contains(f.getId())).toList();
        var ids = recent.stream().map(Feedback::getId).toList();
        var raw = feedbackEvidence.visibleEvidence(ws, ids);
        var changed = feedbackEvidence.practiceChangedAt(raw);
        var projected = new LinkedHashMap<UUID, List<ResultView>>();
        raw.forEach((id, r) -> projected.put(id, views.views(ws, r)));
        Map<UUID, Instant> addressed = ids.isEmpty()
                ? Map.of()
                : jdbc
                        .sql("SELECT feedback_id,min(created_at) FROM feedback_response WHERE workspace_id=:ws AND"
                                + " feedback_id IN (:ids) AND resolution IN ('ADDRESSED','NOT_APPLICABLE') AND"
                                + " created_at<=:until GROUP BY feedback_id")
                        .param("ws", ws)
                        .param("ids", ids)
                        .param("until", window.until())
                        .query((rs, n) -> Map.entry(
                                rs.getObject(1, UUID.class),
                                rs.getObject(2, OffsetDateTime.class).toInstant()))
                        .list()
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        var at = new LinkedHashMap<UUID, Instant>();
        recent.stream()
                .filter(f -> window.contains(f.getCreatedAt()))
                .forEach(f -> at.put(f.getReviewId(), f.getCreatedAt()));
        return new FeedbackFacts(
                recent,
                at,
                projected,
                feedbackEvidence.workResolutions(ws, recent, raw, changed, window.until()),
                feedbackEvidence.workResolutions(ws, recent, raw, changed, window.since()),
                addressed,
                changed);
    }

    private static List<ProfileChangeDTO> feedbackChanges(
            FeedbackFacts facts, OverviewWindow window, Map<Long, ItemRefDTO> targets) {
        List<ProfileChangeDTO> changes = new ArrayList<>();
        for (Feedback feedback : facts.recent()) {
            Instant preparedByRunAt = facts.reviewedAtByRun().get(feedback.getReviewId());
            List<ResultView> evidence = facts.evidenceByFeedback().get(feedback.getId());
            if (preparedByRunAt != null && evidence != null) {
                changes.add(feedbackChange(
                        ProfileChangeType.FEEDBACK_NEW, preparedByRunAt, feedback.getId(), null, evidence, targets));
            }
        }
        for (Feedback feedback : facts.recent()) {
            ProfileChangeDTO reset = feedbackReset(facts, feedback, window, targets);
            if (reset != null) {
                changes.add(reset);
            }
        }
        Set<UUID> answered = new LinkedHashSet<>(facts.resolutionByFeedback().keySet());
        answered.addAll(facts.addressedAt().keySet());
        for (UUID feedbackId : answered) {
            ProfileChangeDTO resolved = feedbackResolved(facts, feedbackId, window, targets);
            if (resolved != null) {
                changes.add(resolved);
            }
        }
        return changes;
    }

    /**
     * Open feedback the developer's work had started to answer and has now fallen back on: its clean run
     * stood somewhere at the window's lower edge and stands at nothing at its upper edge, because work
     * reviewed inside the window raised the problem again. Feedback already closed ({@link FeedbackClosure})
     * cannot fall back: what the work says about it afterwards is new feedback's business, exactly as
     * {@link WorkResolution} puts it.
     */
    private static @Nullable ProfileChangeDTO feedbackReset(
            FeedbackFacts facts, Feedback feedback, OverviewWindow window, Map<Long, ItemRefDTO> targets) {
        UUID feedbackId = feedback.getId();
        WorkResolution atTheEnd = facts.resolutionByFeedback().get(feedbackId);
        WorkResolution atTheStart = facts.resolutionBeforeByFeedback().get(feedbackId);
        List<ResultView> evidence = facts.evidenceByFeedback().get(feedbackId);
        if (atTheEnd == null
                || atTheStart == null
                || evidence == null
                || FeedbackClosure.of(
                                atTheEnd.resolvedAt(),
                                facts.addressedAt().get(feedbackId),
                                facts.practiceChangedAt().get(feedbackId))
                        != null
                || atTheStart.cleanWork().isEmpty()
                || !atTheEnd.cleanWork().isEmpty()) {
            return null;
        }
        List<Work> problems = atTheEnd.problemWork().stream()
                .filter(work -> window.contains(work.at()))
                .toList();
        if (problems.isEmpty()) {
            return null;
        }
        return feedbackChange(
                ProfileChangeType.FEEDBACK_RESET,
                problems.getFirst().at(),
                feedbackId,
                null,
                WorkResolution.CLEAN_NEEDED,
                evidence.getFirst().practice(),
                ProfileChangeDetector.refs(problems, targets));
    }

    /** The resolution its card reports, when it falls inside the window. */
    private static @Nullable ProfileChangeDTO feedbackResolved(
            FeedbackFacts facts, UUID feedbackId, OverviewWindow window, Map<Long, ItemRefDTO> targets) {
        List<ResultView> evidence = facts.evidenceByFeedback().get(feedbackId);
        WorkResolution resolution = facts.resolutionByFeedback().getOrDefault(feedbackId, WorkResolution.NONE);
        FeedbackClosure closure = FeedbackClosure.of(
                resolution.resolvedAt(),
                facts.addressedAt().get(feedbackId),
                facts.practiceChangedAt().get(feedbackId));
        if (evidence == null || closure == null || !window.contains(closure.at())) {
            return null;
        }
        return switch (closure.by()) {
            case WORK ->
                feedbackChange(
                        ProfileChangeType.FEEDBACK_RESOLVED,
                        closure.at(),
                        feedbackId,
                        ProfileChangeResolvedBy.WORK,
                        null,
                        evidence.getFirst().practice(),
                        ProfileChangeDetector.refs(Work.newestFirst(resolution.cleanWork().stream()), targets));
            case DEVELOPER ->
                feedbackChange(
                        ProfileChangeType.FEEDBACK_RESOLVED,
                        closure.at(),
                        feedbackId,
                        ProfileChangeResolvedBy.DEVELOPER,
                        evidence,
                        targets);
            case PRACTICE_CHANGED -> null;
        };
    }

    /** A change citing the feedback's own evidence, newest first. */
    private static ProfileChangeDTO feedbackChange(
            ProfileChangeType type,
            Instant at,
            UUID feedbackId,
            @Nullable ProfileChangeResolvedBy resolvedBy,
            List<ResultView> evidence,
            Map<Long, ItemRefDTO> targets) {
        return feedbackChange(
                type,
                at,
                feedbackId,
                resolvedBy,
                null,
                evidence.getFirst().practice(),
                ProfileChangeDetector.refs(Work.newestFirst(evidence.stream().map(Work::of)), targets));
    }

    /** The practice is the one the feedback is about, read off its newest evidence. */
    private static ProfileChangeDTO feedbackChange(
            ProfileChangeType type,
            Instant at,
            UUID feedbackId,
            @Nullable ProfileChangeResolvedBy resolvedBy,
            @Nullable Integer cleanNeeded,
            Practice practice,
            List<ItemRefDTO> work) {
        PracticeGroup group = practice.getGroup();
        return new ProfileChangeDTO(
                type,
                at,
                practice.getSlug(),
                Objects.requireNonNull(practice.getCurrentVersion().getName()),
                group == null ? null : group.getSlug(),
                group == null ? null : group.getName(),
                null,
                null,
                null,
                feedbackId,
                resolvedBy,
                cleanNeeded,
                work);
    }
}
