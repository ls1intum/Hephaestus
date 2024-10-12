package de.tum.in.www1.hephaestus.gitprovider.pullrequest;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {

    Set<PullRequest> findByAuthor_Login(String authorLogin);

    @Query("""
            SELECT p
            FROM PullRequest p
            JOIN FETCH p.comments
            JOIN FETCH p.reviews
            WHERE p.id = :id
            """)
    Optional<PullRequest> findByIdWithEagerRelations(Long id);
}