package de.tum.in.www1.hephaestus.mentor.document;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for Document entities with versioning.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, DocumentId> {
    /**
     * Find all versions of a document by id, ordered by versionNumber descending
     */
    @Query("SELECT d FROM Document d WHERE d.id = :id AND d.user = :user ORDER BY d.versionNumber DESC")
    List<Document> findByIdAndUserOrderByVersionNumberDesc(@Param("id") UUID id, @Param("user") User user);

    /**
     * Find latest version of a document (highest versionNumber)
     */
    Optional<Document> findFirstByIdAndUserOrderByVersionNumberDesc(UUID id, User user);

    /**
     * Find all versions of a document by ID and user
     */
    List<Document> findByIdAndUser(UUID id, User user);

    /**
     * Find all versions of a document by ID and user with pagination
     */
    Page<Document> findByIdAndUser(UUID id, User user, Pageable pageable);

    /**
     * Find specific document version by ID, user and version number
     */
    Optional<Document> findByIdAndUserAndVersionNumber(UUID id, User user, Integer versionNumber);

    /**
     * Find document versions with versionNumber greater than the specified one
     */
    @Query(
        "SELECT d FROM Document d WHERE d.id = :id AND d.user = :user AND d.versionNumber > :version ORDER BY d.versionNumber DESC"
    )
    List<Document> findByIdAndUserAndVersionNumberGreaterThanOrderByVersionNumberDesc(
        @Param("id") UUID id,
        @Param("user") User user,
        @Param("version") Integer version
    );

    /**
     * Delete document versions with versionNumber greater than the specified one
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Document d WHERE d.id = :id AND d.user = :user AND d.versionNumber > :version")
    void deleteByIdAndUserAndVersionNumberGreaterThan(
        @Param("id") UUID id,
        @Param("user") User user,
        @Param("version") Integer version
    );

    /**
     * Check if document exists for user
     */
    boolean existsByIdAndUser(UUID id, User user);

    // Timestamp-based queries are deprecated with versionNumber. If needed, reintroduce as non-key filters.

    /**
     * Get latest version of each unique document for a user
     */
    @Query(
        """
        SELECT d FROM Document d
        WHERE d.user = :user
        AND d.versionNumber = (
            SELECT MAX(d2.versionNumber)
            FROM Document d2
            WHERE d2.id = d.id AND d2.user = d.user
        )
        ORDER BY d.createdAt DESC
        """
    )
    List<Document> findLatestVersionsByUser(@Param("user") User user);

    /**
     * Find latest versions of all documents by user with pagination
     */
    @Query(
        """
        SELECT d FROM Document d
        WHERE d.user = :user
        AND d.versionNumber = (
            SELECT MAX(d2.versionNumber)
            FROM Document d2
            WHERE d2.id = d.id AND d2.user = :user
        )
        ORDER BY d.createdAt DESC
        """
    )
    Page<Document> findLatestVersionsByUser(@Param("user") User user, Pageable pageable);
}
