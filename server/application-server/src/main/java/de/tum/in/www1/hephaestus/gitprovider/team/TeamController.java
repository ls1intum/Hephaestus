package de.tum.in.www1.hephaestus.gitprovider.team;

import de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermissionRepository;
import de.tum.in.www1.hephaestus.security.EnsureAdminUser;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/team")
public class TeamController {

    private final TeamRepository teamRepo;
    private final TeamInfoDTOConverter converter;
    private final TeamRepositoryPermissionRepository permissionRepository;

    public TeamController(
        TeamRepository teamRepo,
        TeamInfoDTOConverter converter,
        TeamRepositoryPermissionRepository permissionRepository
    ) {
        this.teamRepo = teamRepo;
        this.converter = converter;
        this.permissionRepository = permissionRepository;
    }

    @GetMapping
    public ResponseEntity<List<TeamInfoDTO>> getAllTeams() {
        return ResponseEntity.ok(teamRepo.findAll().stream().map(converter::convert).toList());
    }

    @PostMapping("/{id}/visibility")
    @EnsureAdminUser
    public ResponseEntity<Void> updateTeamVisibility(
        @PathVariable Long id,
        @RequestBody(required = false) Boolean hidden,
        @RequestParam(name = "hidden", required = false) Boolean hiddenParam
    ) {
        // Accept hidden flag from body (preferred) or from query parameter as fallback
        final var resolvedHidden = hidden != null ? hidden : hiddenParam;
        return teamRepo
            .findById(id)
            .map(team -> {
                team.setHidden(Boolean.TRUE.equals(resolvedHidden));
                teamRepo.save(team);
                return ResponseEntity.ok().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{teamId}/repositories/{repositoryId}/visibility")
    @EnsureAdminUser
    public ResponseEntity<Void> updateRepositoryVisibility(
        @PathVariable Long teamId,
        @PathVariable Long repositoryId,
        @RequestBody(required = false) Boolean hiddenFromContributions,
        @RequestParam(name = "hiddenFromContributions", required = false) Boolean hiddenFromContributionsParam
    ) {
        final var resolvedHidden = hiddenFromContributions != null
            ? hiddenFromContributions
            : hiddenFromContributionsParam;

        if (resolvedHidden == null) {
            return ResponseEntity.badRequest().build();
        }

        return permissionRepository
            .findByTeam_IdAndRepository_Id(teamId, repositoryId)
            .map(permission -> {
                permission.setHiddenFromContributions(Boolean.TRUE.equals(resolvedHidden));
                permissionRepository.save(permission);
                return ResponseEntity.ok().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
