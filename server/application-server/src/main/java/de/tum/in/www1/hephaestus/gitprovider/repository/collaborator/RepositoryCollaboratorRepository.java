package de.tum.in.www1.hephaestus.gitprovider.repository.collaborator;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for repository collaborator records.
 *
 * <p>All queries filter by repository ID which inherently carries scope
 * through the Repository -> Organization relationship chain.
 */
public interface RepositoryCollaboratorRepository
    extends JpaRepository<RepositoryCollaborator, RepositoryCollaborator.Id> {
    @Query("SELECT c FROM RepositoryCollaborator c WHERE c.repository.id = :repositoryId AND c.user.id = :userId")
    Optional<RepositoryCollaborator> findByRepositoryIdAndUserId(
        @Param("repositoryId") Long repositoryId,
        @Param("userId") Long userId
    );

    List<RepositoryCollaborator> findByRepository_Id(Long repositoryId);
}
