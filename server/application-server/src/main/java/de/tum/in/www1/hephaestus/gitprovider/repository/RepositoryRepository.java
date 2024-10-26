package de.tum.in.www1.hephaestus.gitprovider.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@org.springframework.stereotype.Repository
public interface RepositoryRepository
        extends JpaRepository<Repository, Long> {

    Repository findByNameWithOwner(String nameWithOwner);

    @Query("""
            SELECT r
            FROM Repository r
            JOIN PullRequest pr ON r.id = pr.repository.id
            WHERE pr.author.login = :contributorLogin
            ORDER BY r.name ASC
            """)
    List<Repository> findContributedByLogin(@Param("contributorLogin") String contributorLogin);
}
