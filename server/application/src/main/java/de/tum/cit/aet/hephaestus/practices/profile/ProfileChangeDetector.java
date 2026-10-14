package de.tum.cit.aet.hephaestus.practices.profile;

import de.tum.cit.aet.hephaestus.practices.profile.dto.ProfileChangeDTO;
import de.tum.cit.aet.hephaestus.practices.result.StandingScale;
import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeGroupStandingDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeStandingDTO;
import de.tum.cit.aet.hephaestus.practices.result.trend.WorkResolution.Work;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding;
import de.tum.cit.aet.hephaestus.practices.spi.ProfileChangeDirection;
import de.tum.cit.aet.hephaestus.practices.spi.ProfileChangeType;
import de.tum.cit.aet.hephaestus.practices.spi.TrendDirection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Reads what moved between two standings snapshots of one developer.
 *
 * <p>Pure on purpose: both snapshots come from the one standing service, read as of the two edges of the
 * window, so this class owns no standing arithmetic — it only compares labels. The evidence it attaches is
 * the work reviewed inside the window for the practice in question, which is exactly what the two snapshots
 * differ by.
 */
final class ProfileChangeDetector {

    private ProfileChangeDetector() {}

    /**
     * @param before the practice standings as of the window's lower edge
     * @param after the practice standings as of its upper edge
     * @param groupsBefore the group standings as of the lower edge
     * @param groupsAfter the group standings as of the upper edge
     * @param windowWorkByPractice the work reviewed inside the window, per practice slug, newest first
     * @param firstObservedAt when each practice first recorded a result about the developer
     * @param targets the runs behind the window's work, for naming it
     */
    static List<ProfileChangeDTO> detect(
            OverviewWindow window,
            List<PracticeStandingDTO> before,
            List<PracticeStandingDTO> after,
            List<PracticeGroupStandingDTO> groupsBefore,
            List<PracticeGroupStandingDTO> groupsAfter,
            Map<String, List<Work>> windowWorkByPractice,
            Map<String, Instant> firstObservedAt,
            Map<Long, ItemRefDTO> targets) {
        Map<String, PracticeStandingDTO> earlier = bySlug(before, PracticeStandingDTO::slug);
        Map<String, PracticeGroupStandingDTO> earlierGroups = bySlug(groupsBefore, PracticeGroupStandingDTO::groupSlug);
        List<ProfileChangeDTO> changes = new ArrayList<>();

        for (PracticeStandingDTO now : after) {
            List<Work> work = windowWorkByPractice.getOrDefault(now.slug(), List.of());
            List<ItemRefDTO> evidence = refs(work, targets);
            Instant at = work.isEmpty() ? window.until() : work.getFirst().at();
            PracticeStandingDTO was = earlier.get(now.slug());
            PracticeStanding standingBefore = was == null ? PracticeStanding.NOT_OBSERVED : was.standing();
            if (standingBefore != now.standing()
                    && (StandingScale.isVerdict(standingBefore) || StandingScale.isVerdict(now.standing()))) {
                changes.add(practiceChange(
                        ProfileChangeType.STANDING_MOVED,
                        at,
                        now,
                        standingBefore.name(),
                        now.standing().name(),
                        direction(standingBefore, now.standing()),
                        evidence));
            }
            TrendDirection directionBefore = was == null ? null : was.direction();
            TrendDirection direction = now.direction();
            // Only a turn one side of which points somewhere: crossing the minimum evidence into UNCERTAIN, or
            // falling back out of it, changes what the trend may claim, not where the developer's work is going.
            if (directionBefore != null
                    && direction != null
                    && directionBefore != direction
                    && ((directionBefore == TrendDirection.IMPROVING || directionBefore == TrendDirection.DECLINING)
                            || (direction == TrendDirection.IMPROVING || direction == TrendDirection.DECLINING))) {
                changes.add(practiceChange(
                        ProfileChangeType.TREND_TURNED,
                        at,
                        now,
                        directionBefore.name(),
                        direction.name(),
                        null,
                        evidence));
            }
            Instant first = firstObservedAt.get(now.slug());
            if (first != null && window.contains(first)) {
                changes.add(practiceChange(ProfileChangeType.FIRST_RECORDED, first, now, null, null, null, evidence));
            }
        }

        Map<String, List<Work>> windowWorkByGroup = windowWorkByGroup(after, windowWorkByPractice);
        for (PracticeGroupStandingDTO now : groupsAfter) {
            PracticeGroupStandingDTO was = earlierGroups.get(now.groupSlug());
            PracticeStanding standingBefore = was == null ? PracticeStanding.NOT_OBSERVED : was.standing();
            if (standingBefore == now.standing()
                    || (!StandingScale.isVerdict(standingBefore) && !StandingScale.isVerdict(now.standing()))) {
                continue;
            }
            List<Work> work = windowWorkByGroup.getOrDefault(now.groupSlug(), List.of());
            changes.add(new ProfileChangeDTO(
                    ProfileChangeType.GROUP_MOVED,
                    work.isEmpty() ? window.until() : work.getFirst().at(),
                    null,
                    null,
                    now.groupSlug(),
                    now.groupName(),
                    standingBefore.name(),
                    now.standing().name(),
                    direction(standingBefore, now.standing()),
                    null,
                    null,
                    null,
                    refs(work, targets)));
        }
        return changes;
    }

    /** Which way a move between two verdicts went, or {@code null} for a move into or out of a silence. */
    private static @Nullable ProfileChangeDirection direction(PracticeStanding from, PracticeStanding to) {
        if (!StandingScale.isVerdict(from) || !StandingScale.isVerdict(to)) {
            return null;
        }
        return standingRank(to) > standingRank(from) ? ProfileChangeDirection.UP : ProfileChangeDirection.DOWN;
    }

    /** The work as the page names it, in the work's order. */
    static List<ItemRefDTO> refs(List<Work> work, Map<Long, ItemRefDTO> targets) {
        return work.stream()
                .map(ref -> targets.get(ref.itemId()))
                .filter(Objects::nonNull)
                .toList();
    }

    private static ProfileChangeDTO practiceChange(
            ProfileChangeType type,
            Instant at,
            PracticeStandingDTO practice,
            @Nullable String from,
            @Nullable String to,
            @Nullable ProfileChangeDirection direction,
            List<ItemRefDTO> evidence) {
        return new ProfileChangeDTO(
                type,
                at,
                practice.slug(),
                practice.name(),
                practice.groupSlug(),
                practice.groupName(),
                from,
                to,
                direction,
                null,
                null,
                null,
                evidence);
    }

    /** The window's work per group: its practices' work merged, one entry per piece of work, newest first. */
    private static Map<String, List<Work>> windowWorkByGroup(
            List<PracticeStandingDTO> practices, Map<String, List<Work>> windowWorkByPractice) {
        Map<String, List<Work>> byGroup = new LinkedHashMap<>();
        for (PracticeStandingDTO practice : practices) {
            if (practice.groupSlug() == null) {
                continue;
            }
            byGroup.merge(
                    practice.groupSlug(),
                    windowWorkByPractice.getOrDefault(practice.slug(), List.of()),
                    (left, right) -> Work.newestFirst(Stream.concat(left.stream(), right.stream())));
        }
        return byGroup;
    }

    private static int standingRank(PracticeStanding value) {
        return switch (value) {
            case DEVELOPING -> 0;
            case MIXED -> 1;
            case STRENGTH -> 2;
            case NOT_OBSERVED, NO_OPPORTUNITY -> -1;
        };
    }

    private static <T> Map<String, T> bySlug(List<T> items, Function<T, String> slug) {
        return items.stream().collect(Collectors.toMap(slug, Function.identity(), (left, ignored) -> left));
    }
}
