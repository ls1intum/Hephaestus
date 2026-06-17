package de.tum.cit.aet.hephaestus.practices.finding;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.finding.dto.DeveloperPracticeSummaryProjection;
import de.tum.cit.aet.hephaestus.practices.finding.dto.ReflectionItemDTO;
import de.tum.cit.aet.hephaestus.practices.finding.dto.ReflectionPracticeDTO;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Polarity;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.PracticeFinding;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for reading practice findings scoped to the authenticated developer.
 *
 * <p>All methods resolve the current user from the security context via
 * {@link UserRepository#getCurrentUser()}. If the user is not yet synced as a
 * developer (e.g., first login before any PR activity), list/summary endpoints
 * return empty results rather than failing.
 *
 * <p>For single-finding access, developer ownership is enforced in SQL — a
 * non-owner receives 404 (not 403) to avoid leaking finding existence.
 */
@Service
@RequiredArgsConstructor
public class PracticeFindingService {

    private final PracticeFindingRepository practiceFindingRepository;
    private final UserRepository userRepository;

    /**
     * Paginated findings for the current user in a workspace, with optional filters.
     *
     * @return empty page if user is not a synced developer
     */
    @Transactional(readOnly = true)
    public Page<PracticeFinding> getFindings(
        Long workspaceId,
        String practiceSlug,
        Observation verdict,
        Pageable pageable
    ) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            return Page.empty(pageable);
        }
        return practiceFindingRepository.findByDeveloperAndWorkspace(
            currentUser.get().getId(),
            workspaceId,
            practiceSlug,
            verdict,
            pageable
        );
    }

    /**
     * Per-practice summary for the current user in a workspace.
     *
     * @return empty list if user is not a synced developer
     */
    @Transactional(readOnly = true)
    public List<DeveloperPracticeSummaryProjection> getSummary(Long workspaceId) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            return List.of();
        }
        return practiceFindingRepository.findSummaryByDeveloperAndWorkspace(currentUser.get().getId(), workspaceId);
    }

    /** Look-back for the reflection surface — mirrors the mentor's findings window. */
    private static final int REFLECTION_LOOKBACK_DAYS = 90;
    /** Upper bound on findings pulled before grouping; the post-group per-practice caps do the real trimming. */
    private static final int MAX_REFLECTION_FINDINGS = 300;
    /** Per-practice cap on "to work on" items — the highest-impact few, not an exhaustive log. */
    private static final int MAX_ITEMS_PER_PRACTICE = 5;
    /** Per-practice cap on acknowledged strengths — enough to affirm, bounded so affirmations don't drown signal. */
    private static final int MAX_STRENGTHS_PER_PRACTICE = 3;

    /**
     * The reflective-dashboard read-model for the current developer: per-practice cards they can READ —
     * why the practice matters, what good looks like, where they stand, what to act on, and what they
     * already do well. This is the third feedback channel (alongside in-context SCM notes and the mentor),
     * reorganising the SAME findings by practice for self-paced reflection — not a scoreboard.
     *
     * <p>Sourced from each target's LATEST review run with {@code NOT_APPLICABLE} already excluded (the
     * repository query), so the surface carries only feedback the developer can act on or be affirmed by.
     * The problem/strength split is single-sourced through {@link Polarity}. Criteria never appears — only
     * the learner framing ({@code whyItMatters}/{@code whatGoodLooksLike}) does.
     *
     * @return empty list if the user is not a synced developer
     */
    @Transactional(readOnly = true)
    public List<ReflectionPracticeDTO> getReflection(Long workspaceId) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            return List.of();
        }
        Instant since = Instant.now().minus(REFLECTION_LOOKBACK_DAYS, ChronoUnit.DAYS);
        List<PracticeFinding> findings = practiceFindingRepository.findRecentByDeveloperAndWorkspace(
            currentUser.get().getId(),
            workspaceId,
            since,
            PageRequest.of(0, MAX_REFLECTION_FINDINGS)
        );

        // Group by practice, preserving first-seen (recency) order from the query.
        Map<String, List<PracticeFinding>> byPractice = new LinkedHashMap<>();
        for (PracticeFinding f : findings) {
            byPractice.computeIfAbsent(f.getPractice().getSlug(), k -> new ArrayList<>()).add(f);
        }

        List<ReflectionPracticeDTO> cards = new ArrayList<>();
        for (List<PracticeFinding> group : byPractice.values()) {
            Practice practice = group.get(0).getPractice();
            Polarity polarity = practice.getPolarity();

            // CRITICAL (ordinal 0) first so the highest-impact item leads the card.
            List<ReflectionItemDTO> toWorkOn = group
                .stream()
                .filter(f -> polarity.isProblem(f.getVerdict()))
                .sorted(Comparator.comparingInt(f -> f.getSeverity().ordinal()))
                .limit(MAX_ITEMS_PER_PRACTICE)
                .map(ReflectionItemDTO::from)
                .toList();
            List<ReflectionItemDTO> strengths = group
                .stream()
                .filter(f -> polarity.isStrength(f.getVerdict()))
                .limit(MAX_STRENGTHS_PER_PRACTICE)
                .map(ReflectionItemDTO::from)
                .toList();
            if (toWorkOn.isEmpty() && strengths.isEmpty()) {
                continue; // defensive: NA is already filtered, so every finding lands in one bucket
            }

            ReflectionPracticeDTO.Standing standing =
                !toWorkOn.isEmpty() && !strengths.isEmpty()
                    ? ReflectionPracticeDTO.Standing.MIXED
                    : !toWorkOn.isEmpty()
                        ? ReflectionPracticeDTO.Standing.NEEDS_WORK
                        : ReflectionPracticeDTO.Standing.STRENGTH;

            PracticeArea area = practice.getArea();
            cards.add(
                new ReflectionPracticeDTO(
                    practice.getSlug(),
                    practice.getName(),
                    area != null ? area.getSlug() : null,
                    area != null ? area.getName() : null,
                    practice.getWhyItMatters(),
                    practice.getWhatGoodLooksLike(),
                    standing,
                    toWorkOn,
                    strengths
                )
            );
        }

        // Lead with what needs attention (worst severity first), then mixed, then pure strengths.
        cards.sort(
            Comparator.<ReflectionPracticeDTO>comparingInt(c -> standingRank(c.standing())).thenComparingInt(
                PracticeFindingService::worstSeverityOrdinal
            )
        );
        return cards;
    }

    private static int standingRank(ReflectionPracticeDTO.Standing s) {
        return switch (s) {
            case NEEDS_WORK -> 0;
            case MIXED -> 1;
            case STRENGTH -> 2;
        };
    }

    private static int worstSeverityOrdinal(ReflectionPracticeDTO card) {
        return card
            .toWorkOn()
            .stream()
            .mapToInt(i -> i.severity().ordinal())
            .min()
            .orElse(Severity.values().length); // strengths-only cards sort after any with problems
    }

    /**
     * Single finding detail. Ownership is enforced in the SQL query itself —
     * a finding belonging to another developer simply won't be returned.
     *
     * @return the finding if it exists and belongs to the current user
     * @throws EntityNotFoundException if no user, or finding not found/not owned
     */
    @Transactional(readOnly = true)
    public PracticeFinding getFinding(Long workspaceId, UUID findingId) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            throw new EntityNotFoundException("PracticeFinding", findingId.toString());
        }
        return practiceFindingRepository
            .findByIdAndDeveloperAndWorkspace(findingId, currentUser.get().getId(), workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("PracticeFinding", findingId.toString()));
    }

    /**
     * All findings for a specific pull request within a workspace.
     * Any workspace member can view PR findings (not restricted to the PR author).
     */
    @Transactional(readOnly = true)
    public List<PracticeFinding> getFindingsForPullRequest(Long workspaceId, Long pullRequestId) {
        return practiceFindingRepository.findByPullRequestAndWorkspace(
            WorkArtifact.PULL_REQUEST,
            pullRequestId,
            workspaceId
        );
    }
}
