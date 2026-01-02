package de.tum.in.www1.hephaestus.gitprovider.discussion;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Discussion entities.
 */
@Repository
public interface DiscussionRepository extends JpaRepository<Discussion, Long> {
    /**
     * Find a discussion by repository ID and discussion number.
     */
    Optional<Discussion> findByRepositoryIdAndNumber(Long repositoryId, int number);

    /**
     * Check if a discussion exists by repository ID and number.
     */
    boolean existsByRepositoryIdAndNumber(Long repositoryId, int number);
}
