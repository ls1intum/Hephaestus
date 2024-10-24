package de.tum.in.www1.hephaestus.codereview.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

@org.springframework.stereotype.Repository
public interface RepositoryRepository
                extends JpaRepository<Repository, Long> {

        Repository findByNameWithOwner(String nameWithOwner);

        @Query("""
                        SELECT r
                        FROM Repository r
                        JOIN FETCH r.pullRequests
                        WHERE r.nameWithOwner = :nameWithOwner
                        """)
        Repository findByNameWithOwnerWithEagerPullRequests(String nameWithOwner);
}
