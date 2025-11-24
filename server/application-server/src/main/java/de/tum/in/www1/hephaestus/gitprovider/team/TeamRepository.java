package de.tum.in.www1.hephaestus.gitprovider.team;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findAllByName(String name);

    List<Team> findAllByHiddenFalse();

    List<Team> findAllByOrganizationIgnoreCase(String organization);

    List<Team> findAllByOrganizationIgnoreCaseAndHiddenFalse(String organization);
}
