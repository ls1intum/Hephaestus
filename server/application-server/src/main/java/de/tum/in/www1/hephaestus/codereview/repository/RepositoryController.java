package de.tum.in.www1.hephaestus.codereview.repository;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/repository")
public class RepositoryController {
    private final RepositoryService repositoryService;

    public RepositoryController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    /**
     * Retrieves all {@link Repository} entities.
     * 
     * @return A list of all Repository entities
     */
    @GetMapping
    public List<Repository> getAllRepositories() {
        return repositoryService.getAllRepositories();
    }

    /**
     * Retrieves a {@link Repository} entity by its full name.
     * 
     * @param nameWithOwner The full name of the Repository
     * @return The Repository entity
     */
    @GetMapping("/{owner}/{name}")
    public Repository getRepositoryByNameWithOwner(@PathVariable String owner, @PathVariable String name) {
        return repositoryService.getRepository(owner + "/" + name);
    }
}
