package de.tum.cit.aet.hephaestus.practices.finding.reaction;

import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.dto.CreateFindingReactionDTO;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.dto.FindingReactionDTO;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.dto.FindingReactionEngagementDTO;
import de.tum.cit.aet.hephaestus.practices.model.PracticeFinding;
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
 * Service for managing developer reaction on AI-generated practice findings.
 *
 * <h2>Authorization</h2>
 * <p>Only the developer who is the subject of a finding may submit reaction on it.
 * This ensures research data integrity — reaction represents the developer's own
 * reaction, not a third party's assessment.
 *
 * <h2>Append-only semantics</h2>
 * <p>Each call to {@link #submitReaction} creates a new row. There is no upsert.
 * The latest reaction per finding is the "current" state for dashboard display.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class FindingReactionService {

    private static final Logger log = LoggerFactory.getLogger(FindingReactionService.class);

    private final FindingReactionRepository reactionRepository;
    private final PracticeFindingRepository findingRepository;
    private final UserRepository userRepository;

    /**
     * Submits reaction on a practice finding. Creates a new append-only record.
     *
     * @param workspaceContext the workspace context (for scoping the finding lookup)
     * @param findingId        the finding to submit a reaction on
     * @param request          the reaction action and optional explanation
     * @return the created reaction DTO
     * @throws EntityNotFoundException  if the finding does not exist in this workspace
     * @throws AccessForbiddenException if the current user is not the finding's developer
     * @throws IllegalArgumentException if DISPUTED without an explanation
     */
    public FindingReactionDTO submitReaction(
        WorkspaceContext workspaceContext,
        UUID findingId,
        CreateFindingReactionDTO request
    ) {
        PracticeFinding finding = findingRepository
            .findByIdAndWorkspaceId(findingId, workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("PracticeFinding", findingId.toString()));

        var currentUser = userRepository.getCurrentUserElseThrow();
        if (!finding.getDeveloper().getId().equals(currentUser.getId())) {
            throw new AccessForbiddenException("Only the finding's developer can submit a reaction");
        }

        if (
            request.action() == FindingReactionAction.DISPUTED &&
            (request.explanation() == null || request.explanation().isBlank())
        ) {
            throw new IllegalArgumentException("Explanation is required when disputing a finding");
        }

        FindingReaction reaction = FindingReaction.builder()
            .finding(finding)
            .findingId(findingId)
            .developer(currentUser)
            .developerId(currentUser.getId())
            .action(request.action())
            .explanation(request.explanation())
            .findingFingerprint(finding.getFindingFingerprint()) // A2: denormalize the stable locus at write time
            .build();

        FindingReaction saved = reactionRepository.save(reaction);
        log.info(
            "Recorded reaction: findingId={}, action={}, developerId={}",
            findingId,
            request.action(),
            currentUser.getId()
        );
        return FindingReactionDTO.from(saved);
    }

    /**
     * Returns the latest reaction by the current user for a specific finding.
     */
    @Transactional(readOnly = true)
    public Optional<FindingReactionDTO> getLatestReaction(WorkspaceContext workspaceContext, UUID findingId) {
        // Verify finding exists in this workspace
        findingRepository
            .findByIdAndWorkspaceId(findingId, workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("PracticeFinding", findingId.toString()));

        var currentUser = userRepository.getCurrentUserElseThrow();
        return reactionRepository
            .findFirstByFindingIdAndDeveloperIdOrderByCreatedAtDesc(findingId, currentUser.getId())
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
            counts.getOrDefault(FindingReactionAction.ENACTED, 0L),
            counts.getOrDefault(FindingReactionAction.DISPUTED, 0L),
            counts.getOrDefault(FindingReactionAction.NOT_APPLICABLE, 0L)
        );
    }

    /**
     * Returns the latest reaction per finding for a given developer.
     * Composable API for enriching finding lists (e.g., for issue #896).
     *
     * @return map of findingId → latest reaction DTO
     */
    @Transactional(readOnly = true)
    public Map<UUID, FindingReactionDTO> getLatestReactionByFindingIds(Collection<UUID> findingIds, Long developerId) {
        if (findingIds.isEmpty()) {
            return Map.of();
        }
        return reactionRepository
            .findLatestByFindingIdsAndDeveloper(findingIds, developerId)
            .stream()
            .collect(Collectors.toMap(FindingReaction::getFindingId, FindingReactionDTO::from));
    }
}
