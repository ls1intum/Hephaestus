package de.tum.in.www1.hephaestus.gitprovider.milestone;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    List<Milestone> findAllByRepository_Id(Long repositoryId);
}
