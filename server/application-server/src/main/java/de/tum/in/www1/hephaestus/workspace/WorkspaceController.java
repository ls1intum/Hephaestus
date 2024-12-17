package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserTeamsDTO;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workspace")
public class WorkspaceController {

    @Autowired
    private WorkspaceService workspaceService;

    @GetMapping("/repositories")
    public ResponseEntity<List<String>> getRepositoriesToMonitor() {
        var repositories = workspaceService.getRepositoriesToMonitor().stream().sorted().toList();
        return ResponseEntity.ok(repositories);
    }

    @PostMapping("/repositories/{owner}/{name}")
    public ResponseEntity<Void> addRepositoryToMonitor(@PathVariable String owner, @PathVariable String name) {
        try {
            workspaceService.addRepositoryToMonitor(owner + '/' + name);
            return ResponseEntity.ok().build();
        } catch (RepositoryNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (RepositoryAlreadyMonitoredException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/repositories/{owner}/{name}")
    public ResponseEntity<Void> removeRepositoryToMonitor(@PathVariable String owner, @PathVariable String name) {
        try {
            workspaceService.removeRepositoryToMonitor(owner + '/' + name);
            return ResponseEntity.ok().build();
        } catch (RepositoryNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserTeamsDTO>> getUsersWithTeams() {
        return ResponseEntity.ok(workspaceService.getUsersWithTeams());
    }

    @PutMapping("/user/{login}/team/{teamId}")
    public ResponseEntity<UserInfoDTO> addTeamToUser(@PathVariable String login, @PathVariable Long teamId) {
        return workspaceService
            .addTeamToUser(login, teamId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/user/{login}/team/{teamId}")
    public ResponseEntity<UserInfoDTO> removeUserFromTeam(@PathVariable String login, @PathVariable Long teamId) {
        return workspaceService
            .removeUserFromTeam(login, teamId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/teams")
    public ResponseEntity<TeamInfoDTO> createTeam(@RequestBody TeamInfoDTO team) {
        return ResponseEntity.ok(workspaceService.createTeam(team.name(), team.color()));
    }

    @PutMapping("/teams/autoassign")
    public ResponseEntity<Void> automaticallyAssignTeams() {
        try {
            workspaceService.automaticallyAssignTeams();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/team/{teamId}/repository/{repositoryOwner}/{repositoryName}")
    public ResponseEntity<TeamInfoDTO> addRepositoryToTeam(
        @PathVariable Long teamId,
        @PathVariable String repositoryOwner,
        @PathVariable String repositoryName
    ) {
        return workspaceService
            .addRepositoryToTeam(teamId, repositoryOwner + '/' + repositoryName)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/team/{teamId}/repository/{repositoryOwner}/{repositoryName}")
    public ResponseEntity<TeamInfoDTO> removeRepositoryFromTeam(
        @PathVariable Long teamId,
        @PathVariable String repositoryOwner,
        @PathVariable String repositoryName
    ) {
        return workspaceService
            .removeRepositoryFromTeam(teamId, repositoryOwner + '/' + repositoryName)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/team/{teamId}/label/{repositoryId}/{label}")
    public ResponseEntity<TeamInfoDTO> addLabelToTeam(
        @PathVariable Long teamId,
        @PathVariable Long repositoryId,
        @PathVariable String label
    ) {
        return workspaceService
            .addLabelToTeam(teamId, repositoryId, label)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/team/{teamId}/label/{labelId}")
    public ResponseEntity<TeamInfoDTO> removeLabelFromTeam(@PathVariable Long teamId, @PathVariable Long labelId) {
        return workspaceService
            .removeLabelFromTeam(teamId, labelId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/team/{teamId}")
    public ResponseEntity<TeamInfoDTO> deleteTeam(@PathVariable Long teamId) {
        return workspaceService
            .deleteTeam(teamId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
