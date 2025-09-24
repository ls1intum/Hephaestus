package de.tum.in.www1.hephaestus.gitprovider.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    Team findFirstByName(String teamName);

    List<Team> findTeamByName(String teamName);
}

