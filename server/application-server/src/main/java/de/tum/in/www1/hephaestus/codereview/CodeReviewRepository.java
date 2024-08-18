package de.tum.in.www1.hephaestus.codereview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CodeReviewRepository
        extends JpaRepository<de.tum.in.www1.hephaestus.codereview.repository.Repository, Long> {

}