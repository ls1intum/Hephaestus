package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserTeamsDTO;
import java.util.List;
import java.util.Map;

import de.tum.in.www1.hephaestus.workspace.dto.*;
import de.tum.in.www1.hephaestus.workspace.exception.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/workspaces")
public class WorkspaceController {

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private WorkspaceLifecycleService workspaceLifecycleService;

    @PostMapping
    public ResponseEntity<?> createWorkspace(@Valid @RequestBody CreateWorkspaceRequestDTO createWorkspaceRequest) {
        try {
            Workspace workspace = workspaceService.createWorkspace(
                createWorkspaceRequest.slug(),
                createWorkspaceRequest.displayName(),
                createWorkspaceRequest.accountLogin(),
                createWorkspaceRequest.accountType(),
                createWorkspaceRequest.ownerUserId()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(WorkspaceDTO.from(workspace));
        } catch (InvalidWorkspaceSlugException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (WorkspaceSlugConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceListItemDTO>> listWorkspaces() {
        List<WorkspaceListItemDTO> workspaces = workspaceService
            .listAllWorkspaces()
            .stream()
            .map(WorkspaceListItemDTO::from)
            .toList();
        return ResponseEntity.ok(workspaces);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<?> getWorkspace(@PathVariable String slug) {
        return workspaceService
            .getWorkspaceBySlug(slug)
            .map(WorkspaceDTO::from)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{slug}/suspend")
    public ResponseEntity<?> suspendWorkspace(@PathVariable String slug) {
        try {
            Workspace workspace = workspaceLifecycleService.suspendWorkspace(slug);
            return ResponseEntity.ok(WorkspaceDTO.from(workspace));
        } catch (WorkspaceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{slug}/resume")
    public ResponseEntity<?> resumeWorkspace(@PathVariable String slug) {
        try {
            Workspace workspace = workspaceLifecycleService.resumeWorkspace(slug);
            return ResponseEntity.ok(WorkspaceDTO.from(workspace));
        } catch (WorkspaceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{slug}")
    public ResponseEntity<?> purgeWorkspace(@PathVariable String slug) {
        try {
            workspaceLifecycleService.purgeWorkspace(slug);
            return ResponseEntity.noContent().build();
        } catch (WorkspaceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{slug}/schedule")
    public ResponseEntity<?> updateSchedule(
        @PathVariable String slug,
        @Valid @RequestBody UpdateWorkspaceScheduleRequestDTO request
    ) {
        try {
            Workspace workspace = workspaceService.updateSchedule(
                slug,
                request.day(),
                request.time()
            );
            return ResponseEntity.ok(WorkspaceDTO.from(workspace));
        } catch (WorkspaceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{slug}/notifications")
    public ResponseEntity<?> updateNotifications(
        @PathVariable String slug,
        @Valid @RequestBody UpdateWorkspaceNotificationsRequestDTO request
    ) {
        try {
            Workspace workspace = workspaceService.updateNotifications(
                slug,
                request.enabled(),
                request.team(),
                request.channelId()
            );
            return ResponseEntity.ok(WorkspaceDTO.from(workspace));
        } catch (WorkspaceNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{slug}/token")
    public ResponseEntity<?> updateToken(
        @PathVariable String slug,
        @Valid @RequestBody UpdateWorkspaceTokenRequestDTO request
    ) {
        try {
            Workspace workspace = workspaceService.updateToken(slug, request.personalAccessToken());
            return ResponseEntity.ok(WorkspaceDTO.from(workspace));
        } catch (WorkspaceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{slug}/slack-credentials")
    public ResponseEntity<?> updateSlackCredentials(
        @PathVariable String slug,
        @Valid @RequestBody UpdateWorkspaceSlackCredentialsRequestDTO request
    ) {
        try {
            Workspace workspace = workspaceService.updateSlackCredentials(
                slug,
                request.slackToken(),
                request.slackSigningSecret()
            );
            return ResponseEntity.ok(WorkspaceDTO.from(workspace));
        } catch (WorkspaceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

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

    @PutMapping("/league/reset")
    public ResponseEntity<Void> resetAndRecalculateLeagues() {
        try {
            workspaceService.resetAndRecalculateLeagues();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
