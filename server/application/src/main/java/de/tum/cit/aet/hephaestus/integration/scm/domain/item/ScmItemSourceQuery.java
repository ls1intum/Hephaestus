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
            value =
                    "SELECT workspace_id AS workspaceId, id AS itemId FROM item WHERE kind = :kind AND source_id = :sourceId AND removed_at IS NULL",
            nativeQuery = true)
    List<WorkspaceItem> issueItems(@Param("kind") String kind, @Param("sourceId") long sourceId);

    @Query(
            value =
                    "SELECT workspace_id AS workspaceId,id AS itemId FROM item WHERE kind=:kind AND source_id=:sourceId",
            nativeQuery = true)
    List<WorkspaceItem> deletedItems(@Param("kind") String kind, @Param("sourceId") long sourceId);

    @Query(value = """
        SELECT p.id AS sourceId,p.repository_id AS repositoryId FROM pull_request_closing_issue link
        JOIN issue p ON p.id=link.pull_request_id WHERE link.issue_id=:issueId AND p.deleted_at IS NULL
        """, nativeQuery = true)
    List<LinkedWork> closingPullRequests(@Param("issueId") long issueId);

    @Query(
            value = "SELECT workspace_id FROM repository_to_monitor WHERE name_with_owner=:name ORDER BY workspace_id",
            nativeQuery = true)
    List<Long> monitoredWorkspaces(@Param("name") String name);

    interface LinkedWork {
        long getSourceId();

        long getRepositoryId();
    }

    interface WorkspaceItem {
        long getWorkspaceId();

        long getItemId();
    }
}
