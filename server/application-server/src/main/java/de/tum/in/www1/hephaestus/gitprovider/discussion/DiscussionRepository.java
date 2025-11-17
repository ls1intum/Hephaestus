package de.tum.in.www1.hephaestus.gitprovider.discussion;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscussionRepository extends JpaRepository<Discussion, Long> {
    Optional<Discussion> findByRepositoryIdAndNumber(long repositoryId, int number);
}
