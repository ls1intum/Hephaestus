package de.tum.in.www1.hephaestus.gitprovider.installation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstallationRepository extends JpaRepository<Installation, Long> {
    List<Installation> findAllByTargetId(Long targetId);
}
