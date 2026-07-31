package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
@WorkspaceAgnostic("Curated revisions are global and owned by their curated practice")
public interface CuratedPracticeRevisionRepository
    extends org.springframework.data.repository.Repository<CuratedPracticeRevision, Long>
{
    CuratedPracticeRevision save(CuratedPracticeRevision revision);

    Optional<CuratedPracticeRevision> findFirstByPracticeIdOrderByRevisionNumberDesc(Long practiceId);
}
