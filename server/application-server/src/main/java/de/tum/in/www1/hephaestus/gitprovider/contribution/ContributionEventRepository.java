package de.tum.in.www1.hephaestus.gitprovider.contribution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContributionEventRepository extends JpaRepository<ContributionEvent, Long> {

    /**
     * Finds a ContributionEvent by its source type and source ID.
     *
     * @param sourceType the type of the contribution
     * @param id         the ID of the contribution source
     * @return an {@link Optional} containing the ContributionEvent if found, or empty otherwise
     */
    Optional<ContributionEvent> findBySourceTypeAndSourceId(ContributionSourceType sourceType, Long id);
}
