package de.tum.in.www1.hephaestus.gitprovider.team;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/team")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping("/all")
    public ResponseEntity<List<TeamInfoDTO>> getTeams() {
        return ResponseEntity.ok(teamService.getAllTeams());
    }

    @PostMapping("/{id}/hide")
    public ResponseEntity<TeamInfoDTO> hideTeam(@PathVariable Long id, @RequestBody Boolean hidden) {
        try {
            return ResponseEntity.ok(teamService.hideTeam(id, hidden));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
