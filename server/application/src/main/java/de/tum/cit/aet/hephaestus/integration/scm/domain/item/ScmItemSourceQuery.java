package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

@WorkspaceAgnostic("SCM mirror changes fan out to each explicitly registered workspace item")
public interface ScmItemSourceQuery extends Repository<Issue, Long> {
    @Query(
            value = "SELECT workspace_id AS workspaceId, id AS itemId FROM item WHERE kind ="
                    + " 'scm.issue' AND source_id = :sourceId AND removed_at IS NULL",
            nativeQuery = true)
    List<WorkspaceItem> issueItems(@Param("sourceId") long sourceId);

    interface WorkspaceItem {
        long getWorkspaceId();

        long getItemId();
    }
}
