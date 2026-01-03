package de.tum.in.www1.hephaestus.gitprovider.milestone;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@WorkspaceAgnostic("Sync operation - Repository ID has workspace through organization")
public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    List<Milestone> findAllByRepository_Id(Long repositoryId);

    @Query(
        """
        SELECT m
        FROM Milestone m
        WHERE m.number = :number
        AND m.repository.id = :repositoryId
        """
    )
    Optional<Milestone> findByNumberAndRepositoryId(
        @Param("number") Integer number,
        @Param("repositoryId") Long repositoryId
    );
}
