package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.model.PracticeVersion;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface ResultPracticeVersionRepository extends Repository<PracticeVersion, Long> {
    List<PracticeVersion> findByWorkspaceIdAndIdIn(long workspaceId, Collection<Long> ids);
}
