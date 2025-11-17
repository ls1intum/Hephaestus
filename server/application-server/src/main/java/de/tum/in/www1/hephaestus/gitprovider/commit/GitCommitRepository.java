package de.tum.in.www1.hephaestus.gitprovider.commit;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GitCommitRepository extends JpaRepository<GitCommit, String> {
    List<GitCommit> findAllByRepositoryIdAndCommittedAtAfterOrderByCommittedAtAsc(Long repositoryId, Instant after);

    @EntityGraph(attributePaths = "fileChanges")
    Optional<GitCommit> findWithFileChangesBySha(String sha);

    @Query(
        """
            SELECT c.sha
            FROM GitCommit c
            WHERE c.repository.id = :repositoryId AND (
                c.additions IS NULL OR c.deletions IS NULL OR c.totalChanges IS NULL OR c.fileChanges IS EMPTY
            )
            ORDER BY c.committedAt DESC
        """
    )
    List<String> findIncompleteCommitShas(@Param("repositoryId") Long repositoryId, Pageable pageable);

    @Query(
        """
            SELECT c.sha
            FROM GitCommit c
            WHERE c.repository.id = :repositoryId AND c.sha IN :shas AND (
                c.additions IS NULL OR c.deletions IS NULL OR c.totalChanges IS NULL OR c.fileChanges IS EMPTY
            )
        """
    )
    List<String> findIncompleteCommitShas(
        @Param("repositoryId") Long repositoryId,
        @Param("shas") Collection<String> shas
    );

}
