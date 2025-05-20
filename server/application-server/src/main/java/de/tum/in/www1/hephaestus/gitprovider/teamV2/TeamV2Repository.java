package de.tum.in.www1.hephaestus.gitprovider.teamV2;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TeamV2Repository extends JpaRepository<TeamV2, Long> {

}
