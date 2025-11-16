package de.tum.in.www1.hephaestus.gitprovider.commit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GitCommitRepository extends JpaRepository<GitCommit, String> {
    List<GitCommit> findAllByRepositoryIdAndCommittedAtAfterOrderByCommittedAtAsc(Long repositoryId, Instant after);

    @EntityGraph(attributePaths = "fileChanges")
    Optional<GitCommit> findWithFileChangesBySha(String sha);
}
