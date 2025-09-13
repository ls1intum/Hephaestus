package de.tum.in.www1.hephaestus.gitprovider.team;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    public TeamController(TeamRepository teamRepo, TeamInfoDTOConverter converter) {
        this.teamRepo = teamRepo;
        this.converter = converter;
    }

    @GetMapping
    public ResponseEntity<List<TeamInfoDTO>> getAllTeams() {
        return ResponseEntity.ok(teamRepo.findAll().stream().map(converter::convert).toList());
    }

    @PostMapping("/{id}/visibility")
    @PreAuthorize("hasAuthority('admin')")
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
                return new ResponseEntity<Void>(HttpStatus.NO_CONTENT);
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
