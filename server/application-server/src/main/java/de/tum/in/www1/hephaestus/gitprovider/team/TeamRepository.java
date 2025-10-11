package de.tum.in.www1.hephaestus.gitprovider.team;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    Team findFirstByName(String teamName);

    List<Team> findAllByName(String name);
}
