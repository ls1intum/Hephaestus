package de.tum.cit.aet.hephaestus.practices.finding.reaction;

import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.dto.CreateFindingReactionDTO;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.dto.FindingReactionDTO;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.dto.FindingReactionEngagementDTO;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
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
 * Service for managing developer reactions to delivered units of feedback.
 *
 * <h2>Authorization</h2>
 * <p>Only the recipient of a feedback unit may submit a reaction to it. This ensures research data
 * integrity — a reaction represents the recipient's own response, not a third party's assessment.
 *
 * <h2>Append-only semantics</h2>
 * <p>Each call to {@link #submitReaction} creates a new row. There is no upsert.
 * The latest reaction per feedback unit is the "current" state for dashboard display.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class FindingReactionService {

    private static final Logger log = LoggerFactory.getLogger(FindingReactionService.class);

    private final FindingReactionRepository reactionRepository;
    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    /**
     * Submits a reaction to a feedback unit. Creates a new append-only record.
     *
     * @param workspaceContext the workspace context (for scoping the feedback lookup)
     * @param feedbackId       the feedback unit to react to
     * @param request          the reaction action and optional explanation
     * @return the created reaction DTO
     * @throws EntityNotFoundException  if the feedback does not exist in this workspace
     * @throws AccessForbiddenException if the current user is not the feedback's recipient
     * @throws IllegalArgumentException if DISPUTED without an explanation
     */
    public FindingReactionDTO submitReaction(
        WorkspaceContext workspaceContext,
        UUID feedbackId,
        CreateFindingReactionDTO request
    ) {
        Feedback feedback = feedbackRepository
            .findByIdAndWorkspaceId(feedbackId, workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("Feedback", feedbackId.toString()));

        var currentUser = userRepository.getCurrentUserElseThrow();
        if (!feedback.getRecipientUserId().equals(currentUser.getId())) {
            throw new AccessForbiddenException("Only the recipient of the feedback can submit a reaction");
        }

        if (
            request.action() == FindingReactionAction.DISPUTED &&
            (request.explanation() == null || request.explanation().isBlank())
        ) {
            throw new IllegalArgumentException("Explanation is required when disputing feedback");
        }

        Reaction reaction = Reaction.builder()
            .feedback(feedback)
            .feedbackId(feedbackId)
            .reactorUserId(currentUser.getId())
            .action(request.action())
            .explanation(request.explanation())
            // A2: denormalize the stable headline locus at write time so B2 can follow it across re-runs.
            .recurrenceKey(feedbackRepository.findHeadlineRecurrenceKey(feedbackId).orElse(null))
            .build();

        Reaction saved = reactionRepository.save(reaction);
        log.info(
            "Recorded reaction: feedbackId={}, action={}, reactorUserId={}",
            feedbackId,
            request.action(),
            currentUser.getId()
        );
        return FindingReactionDTO.from(saved);
    }

    /**
     * Returns the latest reaction by the current user for a specific feedback unit.
     */
    @Transactional(readOnly = true)
    public Optional<FindingReactionDTO> getLatestReaction(WorkspaceContext workspaceContext, UUID feedbackId) {
        // Verify the feedback exists in this workspace
        feedbackRepository
            .findByIdAndWorkspaceId(feedbackId, workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("Feedback", feedbackId.toString()));

        var currentUser = userRepository.getCurrentUserElseThrow();
        return reactionRepository
            .findFirstByFeedbackIdAndReactorUserIdOrderByCreatedAtDesc(feedbackId, currentUser.getId())
            .map(FindingReactionDTO::from);
    }

    /**
     * Returns engagement statistics (action counts) for the current user in this workspace.
     */
    @Transactional(readOnly = true)
    public FindingReactionEngagementDTO getEngagement(WorkspaceContext workspaceContext) {
        var currentUser = userRepository.getCurrentUserElseThrow();
        Map<FindingReactionAction, Long> counts = new EnumMap<>(FindingReactionAction.class);
        reactionRepository
            .countByDeveloperAndWorkspaceGroupByAction(currentUser.getId(), workspaceContext.id())
            .forEach(p -> counts.put(p.getAction(), p.getCount()));
        return new FindingReactionEngagementDTO(
            counts.getOrDefault(FindingReactionAction.ADDRESSED, 0L),
            counts.getOrDefault(FindingReactionAction.DISPUTED, 0L),
            counts.getOrDefault(FindingReactionAction.NOT_APPLICABLE, 0L)
        );
    }

    /**
     * Returns the latest reaction per feedback unit for a given reactor.
     * Composable API for enriching feedback lists.
     *
     * @return map of feedbackId → latest reaction DTO
     */
    @Transactional(readOnly = true)
    public Map<UUID, FindingReactionDTO> getLatestReactionByFeedbackIds(
        Collection<UUID> feedbackIds,
        Long reactorUserId
    ) {
        if (feedbackIds.isEmpty()) {
            return Map.of();
        }
        return reactionRepository
            .findLatestByFeedbackIdsAndReactor(feedbackIds, reactorUserId)
            .stream()
            .collect(Collectors.toMap(Reaction::getFeedbackId, FindingReactionDTO::from));
    }
}
