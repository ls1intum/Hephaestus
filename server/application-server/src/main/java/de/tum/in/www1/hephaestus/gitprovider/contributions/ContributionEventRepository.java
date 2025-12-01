package de.tum.in.www1.hephaestus.gitprovider.contributions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContributionEventRepository extends JpaRepository<ContributionEvent, Long> {

    Optional<ContributionEvent> findBySourceTypeAndSourceId(String sourceType, Long id);
}
