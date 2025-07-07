package de.tum.in.www1.hephaestus.gitprovider.teamV2;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teamsV2")
public class TeamV2Controller {

    private final TeamV2Repository teamRepo;
    private final TeamV2InfoDTOConverter converter;

    public TeamV2Controller(TeamV2Repository teamRepo, TeamV2InfoDTOConverter converter) {
        this.teamRepo = teamRepo;
        this.converter = converter;
    }

    @GetMapping
    public ResponseEntity<List<TeamV2InfoDTO>> getAll() {
        return ResponseEntity.ok(teamRepo.findAll().stream().map(converter::convert).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeamV2InfoDTO> getById(@PathVariable Long id) {
        return teamRepo
            .findById(id)
            .map(converter::convert)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
