package de.tum.cit.aet.hephaestus.practices.profile;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.PracticeGroupService;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.page.FeedbackClosure;
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
import de.tum.cit.aet.hephaestus.practices.spi.ProfileChangeResolvedBy;
import de.tum.cit.aet.hephaestus.practices.spi.ProfileChangeType;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.time.Clock;
import java.time.Instant;
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
    private final ProductReferenceQuery references;
    private final GoingWellQueryService goingWell;
    private final Clock clock;
    private final ProfileFeedbackQuery feedbackFacts;
    private final ProfileOverviewRepository ledger;

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
        var first = ledger.firstOpportunities(ws.id(), account.get()).stream()
                .collect(Collectors.toMap(
                        ProfileOverviewRepository.FirstOpportunity::getSlug,
                        ProfileOverviewRepository.FirstOpportunity::getRecordedAt));
        var feedback = feedbackFacts.find(ws.id(), account.get(), now, window);
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

    private static List<ProfileChangeDTO> feedbackChanges(
            ProfileFeedbackQuery.Facts facts, OverviewWindow window, Map<Long, ItemRefDTO> targets) {
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
            ProfileFeedbackQuery.Facts facts, Feedback feedback, OverviewWindow window, Map<Long, ItemRefDTO> targets) {
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
            ProfileFeedbackQuery.Facts facts, UUID feedbackId, OverviewWindow window, Map<Long, ItemRefDTO> targets) {
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
