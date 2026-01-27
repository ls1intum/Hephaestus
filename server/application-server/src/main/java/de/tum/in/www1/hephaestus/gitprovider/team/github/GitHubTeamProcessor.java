package de.tum.in.www1.hephaestus.gitprovider.team.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.github.dto.GitHubTeamEventDTO;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub teams.
 */
@Service
public class GitHubTeamProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubTeamProcessor.class);

    private final TeamRepository teamRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubTeamProcessor(TeamRepository teamRepository, ApplicationEventPublisher eventPublisher) {
        this.teamRepository = teamRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Process a team without emitting events (for sync operations).
     */
    @Transactional
    public Team process(GitHubTeamEventDTO.GitHubTeamDTO dto, String orgLogin) {
        return process(dto, orgLogin, null);
    }

    @Transactional
    public Team process(GitHubTeamEventDTO.GitHubTeamDTO dto, String orgLogin, ProcessingContext context) {
        if (dto == null || dto.id() == null) {
            log.warn("Skipped team processing: reason=nullOrMissingId");
            return null;
        }

        Team team = teamRepository
            .findById(dto.id())
            .orElseGet(() -> {
                Team t = new Team();
                t.setId(dto.id());
                return t;
            });

        boolean isNew = team.getName() == null;

        // Update fields
        if (dto.name() != null) {
            team.setName(dto.name());
        }
        if (dto.description() != null) {
            team.setDescription(dto.description());
        }
        if (dto.htmlUrl() != null) {
            team.setHtmlUrl(dto.htmlUrl());
        }
        if (dto.privacy() != null) {
            team.setPrivacy(mapPrivacy(dto.privacy()));
        }
        if (dto.createdAt() != null) {
            team.setCreatedAt(dto.createdAt());
        }
        if (dto.updatedAt() != null) {
            team.setUpdatedAt(dto.updatedAt());
        }
        if (isNew && orgLogin != null) {
            team.setOrganization(orgLogin);
        }

        Team saved = teamRepository.save(team);

        if (context != null) {
            if (isNew) {
                eventPublisher.publishEvent(
                    new DomainEvent.TeamCreated(EventPayload.TeamData.from(saved), EventContext.from(context))
                );
                log.debug("Created team: teamId={}, teamSlug={}", saved.getId(), saved.getName());
            } else {
                eventPublisher.publishEvent(
                    new DomainEvent.TeamUpdated(
                        EventPayload.TeamData.from(saved),
                        Set.of("name", "description"),
                        EventContext.from(context)
                    )
                );
                log.debug("Updated team: teamId={}, teamSlug={}", saved.getId(), saved.getName());
            }
        }

        return saved;
    }

    /**
     * Delete a team without emitting events (for sync operations).
     */
    @Transactional
    public void delete(Long teamId) {
        delete(teamId, null);
    }

    @Transactional
    public void delete(Long teamId, ProcessingContext context) {
        if (teamId == null) {
            return;
        }

        teamRepository
            .findById(teamId)
            .ifPresent(team -> {
                String teamName = team.getName();
                teamRepository.delete(team);
                if (context != null) {
                    eventPublisher.publishEvent(
                        new DomainEvent.TeamDeleted(teamId, teamName, EventContext.from(context))
                    );
                }
                log.info("Deleted team: teamId={}, teamSlug={}", teamId, teamName);
            });
    }

    public boolean exists(Long teamId) {
        return teamId != null && teamRepository.existsById(teamId);
    }

    private Team.Privacy mapPrivacy(String privacy) {
        if (privacy == null) {
            return Team.Privacy.VISIBLE;
        }
        return switch (privacy.toLowerCase()) {
            case "secret" -> Team.Privacy.SECRET;
            // "visible" from GraphQL, "closed" from REST API - both map to VISIBLE
            case "visible", "closed" -> Team.Privacy.VISIBLE;
            default -> Team.Privacy.VISIBLE;
        };
    }
}
