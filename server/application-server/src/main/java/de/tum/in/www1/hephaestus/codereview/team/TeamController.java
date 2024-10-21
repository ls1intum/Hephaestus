package de.tum.in.www1.hephaestus.codereview.team;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/team")
public class TeamController {
    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping("/all")
    public ResponseEntity<List<TeamDTO>> getTeams() {
        return ResponseEntity.ok(teamService.getAllTeams());
    }
}
