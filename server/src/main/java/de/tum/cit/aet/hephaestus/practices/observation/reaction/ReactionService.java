package de.tum.cit.aet.hephaestus.practices.observation.reaction;

import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.dto.CreateReactionDTO;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.dto.ReactionDTO;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.dto.ReactionEngagementDTO;
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
public class ReactionService {

    private static final Logger log = LoggerFactory.getLogger(ReactionService.class);

    private final ReactionRepository reactionRepository;
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
    public ReactionDTO submitReaction(
        WorkspaceContext workspaceContext,
        UUID feedbackId,
        CreateReactionDTO request
    ) {
        Feedback feedback = feedbackRepository
            .findByIdAndWorkspaceId(feedbackId, workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("Feedback", feedbackId.toString()));

        var currentUser = userRepository.getCurrentUserElseThrow();
        if (!feedback.getRecipientUserId().equals(currentUser.getId())) {
            throw new AccessForbiddenException("Only the recipient of the feedback can submit a reaction");
        }

        if (
            request.action() == ReactionAction.DISPUTED &&
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
        return ReactionDTO.from(saved);
    }

    /**
     * Returns the latest reaction by the current user for a specific feedback unit.
     */
    @Transactional(readOnly = true)
    public Optional<ReactionDTO> getLatestReaction(WorkspaceContext workspaceContext, UUID feedbackId) {
        // Verify the feedback exists in this workspace
        feedbackRepository
            .findByIdAndWorkspaceId(feedbackId, workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("Feedback", feedbackId.toString()));

        var currentUser = userRepository.getCurrentUserElseThrow();
        return reactionRepository
            .findFirstByFeedbackIdAndReactorUserIdOrderByCreatedAtDesc(feedbackId, currentUser.getId())
            .map(ReactionDTO::from);
    }

    /**
     * Returns engagement statistics (action counts) for the current user in this workspace.
     */
    @Transactional(readOnly = true)
    public ReactionEngagementDTO getEngagement(WorkspaceContext workspaceContext) {
        var currentUser = userRepository.getCurrentUserElseThrow();
        Map<ReactionAction, Long> counts = new EnumMap<>(ReactionAction.class);
        reactionRepository
            .countByDeveloperAndWorkspaceGroupByAction(currentUser.getId(), workspaceContext.id())
            .forEach(p -> counts.put(p.getAction(), p.getCount()));
        return new ReactionEngagementDTO(
            counts.getOrDefault(ReactionAction.ADDRESSED, 0L),
            counts.getOrDefault(ReactionAction.DISPUTED, 0L),
            counts.getOrDefault(ReactionAction.NOT_APPLICABLE, 0L)
        );
    }

    /**
     * Returns the latest reaction per feedback unit for a given reactor.
     * Composable API for enriching feedback lists.
     *
     * @return map of feedbackId → latest reaction DTO
     */
    @Transactional(readOnly = true)
    public Map<UUID, ReactionDTO> getLatestReactionByFeedbackIds(
        Collection<UUID> feedbackIds,
        Long reactorUserId
    ) {
        if (feedbackIds.isEmpty()) {
            return Map.of();
        }
        return reactionRepository
            .findLatestByFeedbackIdsAndReactor(feedbackIds, reactorUserId)
            .stream()
            .collect(Collectors.toMap(Reaction::getFeedbackId, ReactionDTO::from));
    }
}
