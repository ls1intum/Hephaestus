package de.tum.in.www1.hephaestus.gitprovider.team;

import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByName(String name);

    @Query("""
            SELECT t
            FROM Team t
            LEFT JOIN FETCH t.labels
            WHERE EXISTS (
                SELECT l
                FROM t.labels l
                WHERE l IN :labels
            )
            """)
    List<Team> findAllByPullRequestLabels(@Param("labels") Set<Label> labels);

    @Query("""
            SELECT t
            FROM Team t
            LEFT JOIN FETCH t.repositories
            WHERE EXISTS (
                SELECT r
                FROM t.repositories r
                WHERE r.nameWithOwner = :repositoryNameWithOwner
            )
            """)
    List<Team> findAllByRepositoryName(@Param("repositoryNameWithOwner") String repositoryNameWithOwner);
}
