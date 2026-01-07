package de.tum.in.www1.hephaestus.gitprovider.milestone;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for milestone entities.
 *
 * <p>All queries filter by repository ID which inherently carries workspace scope
 * through the Repository -> Organization -> Workspace.organization chain.
 */
@Repository
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
