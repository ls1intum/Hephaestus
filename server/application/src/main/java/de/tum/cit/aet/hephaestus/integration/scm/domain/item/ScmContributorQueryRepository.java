package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

@WorkspaceAgnostic("Reads contributors of an already authorized native SCM mirror")
public interface ScmContributorQueryRepository extends Repository<Issue, Long> {
    @Query(value = """
        SELECT author_id FROM issue_comment WHERE issue_id=:id AND author_id IS NOT NULL
        """, nativeQuery = true)
    List<Long> issueCommentAuthors(@Param("id") long id);

    @Query(value = """
        SELECT author_id FROM pull_request_review_comment WHERE pull_request_id=:id AND author_id IS NOT NULL
        """, nativeQuery = true)
    List<Long> reviewCommentAuthors(@Param("id") long id);

    @Query(value = """
        SELECT author_id FROM pull_request_review WHERE pull_request_id=:id AND author_id IS NOT NULL
        """, nativeQuery = true)
    List<Long> reviewAuthors(@Param("id") long id);

    @Query(value = """
        SELECT resolved_by_id FROM pull_request_review_thread WHERE pull_request_id=:id AND resolved_by_id IS NOT NULL
        """, nativeQuery = true)
    List<Long> threadResolvers(@Param("id") long id);

    @Query(value = """
        SELECT DISTINCT roles.user_id FROM commit_pull_request cp JOIN (SELECT id AS commit_id,author_id AS user_id FROM git_commit UNION ALL SELECT id,committer_id FROM git_commit UNION ALL SELECT commit_id,user_id FROM commit_contributor) roles ON roles.commit_id=cp.commit_id WHERE cp.pull_request_id=:id AND roles.user_id IS NOT NULL
        """, nativeQuery = true)
    List<Long> diffContributors(@Param("id") long id);

    @Query(value = """
        SELECT DISTINCT roles.user_id FROM git_commit c JOIN (SELECT id AS commit_id,author_id AS user_id FROM git_commit UNION ALL SELECT id,committer_id FROM git_commit UNION ALL SELECT commit_id,user_id FROM commit_contributor) roles ON roles.commit_id=c.id WHERE c.repository_id=:id AND roles.user_id IS NOT NULL
        """, nativeQuery = true)
    List<Long> treeContributors(@Param("id") long id);
}
