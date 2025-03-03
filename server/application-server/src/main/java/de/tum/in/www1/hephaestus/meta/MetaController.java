package de.tum.in.www1.hephaestus.meta;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/meta")
public class MetaController {

    @Autowired
    private MetaService metaService;

    @GetMapping
    public ResponseEntity<MetaDataDTO> getMetaData() {
        return ResponseEntity.ok(metaService.getMetaData());
    }

    @GetMapping("/contributors")
    public ResponseEntity<List<ContributorDTO>> getContributors() {
        return ResponseEntity.ok(metaService.getContributors());
    }
}
