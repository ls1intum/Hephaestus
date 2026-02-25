package de.tum.in.www1.hephaestus.gitprovider.commit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for CommitContributor entities.
 */
@Repository
public interface CommitContributorRepository extends JpaRepository<CommitContributor, Long> {
    /**
     * Find all contributors for a commit, ordered by role and ordinal.
     */
    @Query(
        """
        SELECT cc FROM CommitContributor cc
        LEFT JOIN FETCH cc.user
        WHERE cc.commit.id = :commitId
        ORDER BY cc.role, cc.ordinal
        """
    )
    List<CommitContributor> findByCommitIdWithUser(@Param("commitId") Long commitId);

    /**
     * Upsert a commit contributor. On conflict (same commit, email, role),
     * updates the name, ordinal, and user_id if a better resolution is available.
     * <p>
     * The user_id is validated against the "user" table via a subquery to avoid FK violations
     * when the GitHub user hasn't been synced into our database yet.
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO commit_contributor (commit_id, user_id, role, name, email, ordinal)
        VALUES (:commitId, (SELECT id FROM "user" WHERE id = :userId), :role, :name, :email, :ordinal)
        ON CONFLICT ON CONSTRAINT uq_commit_contributor_commit_email_role DO UPDATE SET
            name = COALESCE(EXCLUDED.name, commit_contributor.name),
            ordinal = EXCLUDED.ordinal,
            user_id = COALESCE(EXCLUDED.user_id, commit_contributor.user_id)
        """,
        nativeQuery = true
    )
    void upsertContributor(
        @Param("commitId") Long commitId,
        @Param("userId") Long userId,
        @Param("role") String role,
        @Param("name") String name,
        @Param("email") String email,
        @Param("ordinal") int ordinal
    );

    /**
     * Delete all contributors for a commit.
     */
    void deleteByCommitId(Long commitId);
}
