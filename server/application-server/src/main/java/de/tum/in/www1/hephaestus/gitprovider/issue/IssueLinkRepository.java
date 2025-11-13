package de.tum.in.www1.hephaestus.gitprovider.issue;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IssueLinkRepository extends JpaRepository<IssueLink, Long> {
    List<IssueLink> findBySourceIdAndType(Long sourceId, IssueLinkType type);
    List<IssueLink> findByTargetIdAndType(Long targetId, IssueLinkType type);
    void deleteBySourceIdAndType(Long sourceId, IssueLinkType type);
    void deleteByTargetIdAndType(Long targetId, IssueLinkType type);
    void deleteBySourceIdAndTargetIdAndType(Long sourceId, Long targetId, IssueLinkType type);
}
