package de.tum.in.www1.hephaestus.practices.finding.feedback;

import de.tum.in.www1.hephaestus.core.exception.AccessForbiddenException;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.in.www1.hephaestus.practices.finding.feedback.dto.CreateFindingFeedbackDTO;
import de.tum.in.www1.hephaestus.practices.finding.feedback.dto.FindingFeedbackDTO;
import de.tum.in.www1.hephaestus.practices.finding.feedback.dto.FindingFeedbackEngagementDTO;
import de.tum.in.www1.hephaestus.practices.model.PracticeFinding;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing contributor feedback on AI-generated practice findings.
 *
 * <h2>Authorization</h2>
 * <p>Only the contributor who is the subject of a finding may submit feedback on it.
 * This ensures research data integrity — feedback represents the contributor's own
 * reaction, not a third party's assessment.
 *
 * <h2>Append-only semantics</h2>
 * <p>Each call to {@link #submitFeedback} creates a new row. There is no upsert.
 * The latest feedback per finding is the "current" state for dashboard display.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class FindingFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FindingFeedbackService.class);

    private final FindingFeedbackRepository feedbackRepository;
    private final PracticeFindingRepository findingRepository;
    private final UserRepository userRepository;

    /**
     * Submits feedback on a practice finding. Creates a new append-only record.
     *
     * @param workspaceContext the workspace context (for scoping the finding lookup)
     * @param findingId        the finding to provide feedback on
     * @param request          the feedback action and optional explanation
     * @return the created feedback DTO
     * @throws EntityNotFoundException  if the finding does not exist in this workspace
     * @throws AccessForbiddenException if the current user is not the finding's contributor
     * @throws IllegalArgumentException if DISPUTED without an explanation
     */
    public FindingFeedbackDTO submitFeedback(
        WorkspaceContext workspaceContext,
        UUID findingId,
        CreateFindingFeedbackDTO request
    ) {
        PracticeFinding finding = findingRepository
            .findByIdAndWorkspaceId(findingId, workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("PracticeFinding", findingId.toString()));

        var currentUser = userRepository.getCurrentUserElseThrow();
        if (!finding.getContributor().getId().equals(currentUser.getId())) {
            throw new AccessForbiddenException("Only the finding's contributor can provide feedback");
        }

        if (
            request.action() == FindingFeedbackAction.DISPUTED &&
            (request.explanation() == null || request.explanation().isBlank())
        ) {
            throw new IllegalArgumentException("Explanation is required when disputing a finding");
        }

        FindingFeedback feedback = FindingFeedback.builder()
            .finding(finding)
            .findingId(findingId)
            .contributor(currentUser)
            .contributorId(currentUser.getId())
            .action(request.action())
            .explanation(request.explanation())
            .build();

        FindingFeedback saved = feedbackRepository.save(feedback);
        log.info(
            "Recorded feedback: findingId={}, action={}, contributorId={}",
            findingId,
            request.action(),
            currentUser.getId()
        );
        return FindingFeedbackDTO.from(saved);
    }

    /**
     * Returns the latest feedback by the current user for a specific finding.
     */
    @Transactional(readOnly = true)
    public Optional<FindingFeedbackDTO> getLatestFeedback(WorkspaceContext workspaceContext, UUID findingId) {
        // Verify finding exists in this workspace
        findingRepository
            .findByIdAndWorkspaceId(findingId, workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("PracticeFinding", findingId.toString()));

        var currentUser = userRepository.getCurrentUserElseThrow();
        return feedbackRepository
            .findFirstByFindingIdAndContributorIdOrderByCreatedAtDesc(findingId, currentUser.getId())
            .map(FindingFeedbackDTO::from);
    }

    /**
     * Returns engagement statistics (action counts) for the current user in this workspace.
     */
    @Transactional(readOnly = true)
    public FindingFeedbackEngagementDTO getEngagement(WorkspaceContext workspaceContext) {
        var currentUser = userRepository.getCurrentUserElseThrow();
        Map<FindingFeedbackAction, Long> counts = new EnumMap<>(FindingFeedbackAction.class);
        feedbackRepository
            .countByContributorAndWorkspaceGroupByAction(currentUser.getId(), workspaceContext.id())
            .forEach(p -> counts.put(p.getAction(), p.getCount()));
        return new FindingFeedbackEngagementDTO(
            counts.getOrDefault(FindingFeedbackAction.APPLIED, 0L),
            counts.getOrDefault(FindingFeedbackAction.DISPUTED, 0L),
            counts.getOrDefault(FindingFeedbackAction.NOT_APPLICABLE, 0L)
        );
    }

    /**
     * Returns the latest feedback per finding for a given contributor.
     * Composable API for enriching finding lists (e.g., for issue #896).
     *
     * @return map of findingId → latest feedback DTO
     */
    @Transactional(readOnly = true)
    public Map<UUID, FindingFeedbackDTO> getLatestFeedbackByFindingIds(
        Collection<UUID> findingIds,
        Long contributorId
    ) {
        if (findingIds.isEmpty()) {
            return Map.of();
        }
        return feedbackRepository
            .findLatestByFindingIdsAndContributor(findingIds, contributorId)
            .stream()
            .collect(Collectors.toMap(FindingFeedback::getFindingId, FindingFeedbackDTO::from));
    }
}
