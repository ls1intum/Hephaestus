package de.tum.in.www1.hephaestus.codereview.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

@org.springframework.stereotype.Repository
public interface RepositoryRepository
                extends JpaRepository<Repository, Long> {

        @Query("SELECT r FROM Repository r WHERE r.nameWithOwner = ?1")
        Repository findByNameWithOwner(String nameWithOwner);
}
