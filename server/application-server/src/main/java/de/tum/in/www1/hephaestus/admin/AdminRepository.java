package de.tum.in.www1.hephaestus.admin;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRepository extends JpaRepository<AdminConfig, Long> {
    Optional<AdminConfig> findFirstByOrderByIdAsc();
}
