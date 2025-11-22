package de.tum.in.www1.hephaestus.meta;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/contributors")
@RequiredArgsConstructor
public class ContributorController {

    private final MetaService metaService;

    @GetMapping
    public ResponseEntity<List<ContributorDTO>> listGlobalContributors() {
        return ResponseEntity.ok(metaService.getGlobalContributors());
    }
}
