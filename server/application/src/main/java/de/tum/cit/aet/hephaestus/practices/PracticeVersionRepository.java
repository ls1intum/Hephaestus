package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.model.PracticeVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

@WorkspaceAgnostic("Versions are read through a tenant-validated owning practice")
public interface PracticeVersionRepository extends JpaRepository<PracticeVersion, Long> {
    Optional<PracticeVersion> findFirstByPracticeIdOrderByNumberDesc(Long practiceId);

    List<PracticeVersion> findByPracticeIdOrderByNumberAsc(Long practiceId);

    Optional<PracticeVersion> findFirstByPracticeIdAndNumberLessThanOrderByNumberDesc(Long practiceId, int number);

    @Modifying
    @Query("DELETE FROM PracticeVersion v WHERE v.workspaceId = :workspaceId")
    void deleteAllByWorkspaceId(Long workspaceId);
}
