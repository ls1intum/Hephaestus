package de.tum.in.www1.hephaestus.mentor.document;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Document entities with versioning.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, DocumentId> {

    /**
     * Find all versions of a document by id, ordered by createdAt descending
     */
    @Query("SELECT d FROM Document d WHERE d.id = :id AND d.user = :user ORDER BY d.createdAt DESC")
    List<Document> findByIdAndUserOrderByCreatedAtDesc(@Param("id") UUID id, @Param("user") User user);

    /**
     * Find latest version of a document (most recent createdAt)
     */
    @Query("SELECT d FROM Document d WHERE d.id = :id AND d.user = :user ORDER BY d.createdAt DESC LIMIT 1")
    Optional<Document> findFirstByIdAndUserOrderByCreatedAtDesc(@Param("id") UUID id, @Param("user") User user);

    /**
     * Find all versions of a document by ID and user
     */
    List<Document> findByIdAndUser(UUID id, User user);

    /**
     * Find all versions of a document by ID and user with pagination
     */
    Page<Document> findByIdAndUser(UUID id, User user, Pageable pageable);

    /**
     * Find specific document version by ID, user and timestamp
     */
    Optional<Document> findByIdAndUserAndCreatedAt(UUID id, User user, Instant createdAt);

    /**
     * Check if document exists for user
     */
    boolean existsByIdAndUser(UUID id, User user);

    /**
     * Find document versions created after specified timestamp
     */
    @Query("SELECT d FROM Document d WHERE d.id = :id AND d.user = :user AND d.createdAt > :timestamp ORDER BY d.createdAt DESC")
    List<Document> findByIdAndUserAndCreatedAtAfterOrderByCreatedAtDesc(
        @Param("id") UUID id, 
        @Param("user") User user, 
        @Param("timestamp") Instant timestamp
    );

    /**
     * Delete document versions created after specified timestamp
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Document d WHERE d.id = :id AND d.user = :user AND d.createdAt > :timestamp")
    void deleteByIdAndUserAndCreatedAtAfter(
        @Param("id") UUID id, 
        @Param("user") User user, 
        @Param("timestamp") Instant timestamp
    );

    /**
     * Get latest version of each unique document for a user
     */
    @Query("""
        SELECT d FROM Document d 
        WHERE d.user = :user 
        AND d.createdAt = (
            SELECT MAX(d2.createdAt) 
            FROM Document d2 
            WHERE d2.id = d.id AND d2.user = d.user
        )
        ORDER BY d.createdAt DESC
        """)
    List<Document> findLatestVersionsByUser(@Param("user") User user);

    /**
     * Find latest versions of all documents by user with pagination
     */
    @Query("""
        SELECT d FROM Document d 
        WHERE d.user = :user 
        AND d.createdAt = (
            SELECT MAX(d2.createdAt) 
            FROM Document d2 
            WHERE d2.id = d.id AND d2.user = :user
        )
        ORDER BY d.createdAt DESC
        """)
    Page<Document> findLatestVersionsByUser(@Param("user") User user, Pageable pageable);
}
