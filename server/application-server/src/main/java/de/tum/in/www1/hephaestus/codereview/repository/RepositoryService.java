package de.tum.in.www1.hephaestus.codereview.repository;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RepositoryService {
    private static final Logger logger = LoggerFactory.getLogger(Repository.class);

    private final RepositoryRepository repositoryRepository;

    public RepositoryService(RepositoryRepository repositoryRepository) {
        this.repositoryRepository = repositoryRepository;
    }

    /**
     * Retrieves all {@link Repository} entities.
     * 
     * @return A list of all Repository entities
     */
    public List<Repository> getAllRepositories() {
        var repositories = repositoryRepository.findAll();
        logger.info("Getting Repositories: {}", repositories.toArray());
        return repositoryRepository.findAll();
    }

    public Repository getRepository(String nameWithOwner) {
        return repositoryRepository.findByNameWithOwner(nameWithOwner);
    }

    /**
     * Creates a new {@link Repository} entity with the current timestamp and saves
     * it to
     * the repository.
     * 
     * @return The created Repository entity
     */
    public Repository addRepository() {
        Repository repository = new Repository();
        repository.setAddedAt(Instant.now());
        logger.info("Adding new Repository with timestamp: {}", repository.getAddedAt());
        return repositoryRepository.save(repository);
    }

    public Repository saveRepository(Repository repository) {
        Repository existingRepository = repositoryRepository.findByNameWithOwner(repository.getNameWithOwner());
        logger.info("Found Repository: {}", existingRepository);
        if (existingRepository != null) {
            logger.info("Repository already exists: {}", existingRepository.getNameWithOwner());
            return existingRepository;
        }

        logger.info("Adding Repository: {}", repository.getNameWithOwner());
        return repositoryRepository.save(repository);
    }

    public long countRepositories() {
        return repositoryRepository.count();
    }
}
