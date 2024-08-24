package de.tum.in.www1.hephaestus.codereview.pullrequest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.hephaestus.codereview.actor.Actor;

@Repository
public interface PullrequestRepository extends JpaRepository<Pullrequest, Long> {

    @Query("SELECT p FROM Pullrequest p WHERE p.githubId = ?1")
    Pullrequest findByGithubId(Long githubId);

    @Query("SELECT p FROM Pullrequest p WHERE p.author = ?1")
    Pullrequest findByAuthor(Actor author);

}
