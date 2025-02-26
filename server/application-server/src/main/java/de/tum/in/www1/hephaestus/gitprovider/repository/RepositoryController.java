package de.tum.in.www1.hephaestus.gitprovider.repository;

import de.tum.in.www1.hephaestus.gitprovider.contributor.ContributorInfoDTO;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/repository")
public class RepositoryController {

    @Autowired
    private RepositoryService repositoryService;

    @GetMapping("/{owner}/{repo}/contributors")
    public ResponseEntity<List<ContributorInfoDTO>> getContributorsByRepositoryName(
        @PathVariable String owner,
        @PathVariable String repo
    ) {
        String nameWithOwner = owner + "/" + repo;
        return ResponseEntity.ok(repositoryService.getContributorsByRepositoryName(nameWithOwner));
    }
}
