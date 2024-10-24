package de.tum.in.www1.hephaestus.admin;

import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.hephaestus.codereview.team.TeamDTO;
import de.tum.in.www1.hephaestus.codereview.user.UserDTO;
import de.tum.in.www1.hephaestus.codereview.user.UserTeamsDTO;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public String admin() {
        return "Welcome to the admin page!";
    }

    @GetMapping("/me")
    public UserInfoDto getGretting(JwtAuthenticationToken auth) {
        return new UserInfoDto(
                auth.getToken().getClaimAsString(StandardClaimNames.PREFERRED_USERNAME),
                auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }

    public static record UserInfoDto(String name, List<String> roles) {
    }

    @GetMapping("/config")
    public ResponseEntity<AdminConfig> getConfig() {
        try {
            return ResponseEntity.ok(adminService.getAdminConfig());
        } catch (NoAdminConfigFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/config/repositories")
    public ResponseEntity<Set<String>> updateRepositories(@RequestBody List<String> repositories) {
        return ResponseEntity.ok(adminService.updateRepositories(Set.copyOf(repositories)));
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserTeamsDTO>> getUsersAsAdmin() {
        return ResponseEntity.ok(adminService.getUsersAsAdmin());
    }

    @PutMapping("/users/teamadd/{login}/{teamId}")
    public ResponseEntity<UserDTO> addTeamToUser(@PathVariable String login, @PathVariable Long teamId) {
        return adminService.addTeamToUser(login, teamId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/users/teamremove/{login}/{teamId}")
    public ResponseEntity<UserDTO> removeTeamFromUser(@PathVariable String login, @PathVariable Long teamId) {
        return adminService.removeTeamFromUser(login, teamId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/teams")
    public ResponseEntity<TeamDTO> createTeam(@RequestBody TeamDTO team) {
        return ResponseEntity.ok(adminService.createTeam(team.name(), team.color()));
    }

    @DeleteMapping("/teams/{teamId}")
    public ResponseEntity<TeamDTO> deleteTeam(@PathVariable Long teamId) {
        return adminService.deleteTeam(teamId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
