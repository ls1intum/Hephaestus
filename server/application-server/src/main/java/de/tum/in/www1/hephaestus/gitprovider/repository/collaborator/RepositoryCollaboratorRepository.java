package de.tum.in.www1.hephaestus.gitprovider.repository.collaborator;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepositoryCollaboratorRepository
    extends JpaRepository<RepositoryCollaborator, RepositoryCollaborator.Id> {
    @Query("SELECT c FROM RepositoryCollaborator c WHERE c.repository.id = :repositoryId AND c.user.id = :userId")
    Optional<RepositoryCollaborator> findByRepositoryIdAndUserId(
        @Param("repositoryId") Long repositoryId,
        @Param("userId") Long userId
    );

    List<RepositoryCollaborator> findByRepository_Id(Long repositoryId);

    /**
     * Atomic upsert using PostgreSQL's INSERT ON CONFLICT.
     * <p>
     * Inserts a new collaborator or updates the permission if the (repository_id, user_id) already exists.
     * This avoids race conditions in concurrent sync operations where multiple threads may try to
     * insert the same collaborator simultaneously.
     * <p>
     * <strong>Important:</strong> This native query bypasses Hibernate's entity management.
     * The {@code flushAutomatically} attribute ensures any pending Hibernate changes are written
     * before this query executes. The {@code clearAutomatically} attribute evicts all entities
     * from the persistence context after execution, preventing stale reads of collaborator data.
     *
     * @param repositoryId the repository ID (part of composite PK)
     * @param userId the user ID (part of composite PK)
     * @param permission the permission level as a string (enum name)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        INSERT INTO repository_collaborator (repository_id, user_id, permission)
        VALUES (:repositoryId, :userId, :permission)
        ON CONFLICT (repository_id, user_id)
        DO UPDATE SET permission = EXCLUDED.permission
        """,
        nativeQuery = true
    )
    void upsert(
        @Param("repositoryId") Long repositoryId,
        @Param("userId") Long userId,
        @Param("permission") String permission
    );
}
