package de.tum.cit.aet.hephaestus.integration.scm;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentity;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonScope;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonSourceIdentityContributor;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonSourceNamespace;
import de.tum.cit.aet.hephaestus.core.security.ScmOrigin;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Component;

/** One source-role relation for exact scope selection and post-sync processing admission. */
@Component
@RequiredArgsConstructor
@WorkspaceAgnostic("Instance person scope and explicitly workspace-bound source attribution")
public class ScmPersonSourceIdentityContributor implements PersonSourceIdentityContributor {
    private final JdbcTemplate jdbc;
    private static final String ROLES = """
        SELECT id AS source_id,author_id AS user_id FROM issue
        UNION ALL SELECT id,merged_by_id FROM issue
        UNION ALL SELECT issue_id,user_id FROM issue_assignee
        UNION ALL SELECT pull_request_id,user_id FROM pull_request_requested_reviewers
        UNION ALL SELECT issue_id,author_id FROM issue_comment
        UNION ALL SELECT pull_request_id,author_id FROM pull_request_review
        UNION ALL SELECT pull_request_id,resolved_by_id FROM pull_request_review_thread
        UNION ALL SELECT pull_request_id,author_id FROM pull_request_review_comment
        UNION ALL SELECT cp.pull_request_id,c.author_id FROM commit_pull_request cp JOIN git_commit c ON c.id=cp.commit_id
        UNION ALL SELECT cp.pull_request_id,c.committer_id FROM commit_pull_request cp JOIN git_commit c ON c.id=cp.commit_id
        UNION ALL SELECT cp.pull_request_id,c.user_id FROM commit_pull_request cp JOIN commit_contributor c ON c.commit_id=cp.commit_id
        """;

    @Override
    public Set<String> itemKinds() {
        return Set.of("scm.issue", "scm.pull_request", "scm.pull_request_review", "scm.repository", "scm.file");
    }

    @Override
    public List<Long> sourceIds(PersonScope person) {
        return jdbc.query(
                "SELECT DISTINCT source_id FROM (" + ROLES + ") roles WHERE user_id=ANY(?) ORDER BY source_id",
                (rs, row) -> rs.getLong(1),
                new SqlArrayValue("bigint", person.userIds().toArray()));
    }

    @Override
    public List<PersonIdentity> identitiesForSource(long workspaceId, String itemKind, long sourceId) {
        // Tree captures omit Git authorship; person attribution stays with the source work.
        if (itemKind.equals("scm.repository") || itemKind.equals("scm.file")) return List.of();
        if (!itemKinds().contains(itemKind)) throw new IllegalArgumentException("Unsupported SCM item kind");
        if (itemKind.equals("scm.pull_request_review")) {
            var parents = jdbc.query(
                    "SELECT parent.source_id FROM item child JOIN item parent ON parent.workspace_id=child.workspace_id AND parent.id=child.parent_id JOIN pull_request_review review ON review.id=child.source_id AND review.pull_request_id=parent.source_id WHERE child.workspace_id=? AND child.kind='scm.pull_request_review' AND child.source_id=? AND parent.kind='scm.pull_request'",
                    (rs, row) -> rs.getLong("source_id"),
                    workspaceId,
                    sourceId);
            return parents.isEmpty()
                    ? List.of()
                    : identitiesForSource(workspaceId, "scm.pull_request", parents.getFirst());
        }
        boolean connected = connectedToWorkspace(workspaceId, sourceId);
        return jdbc.query(
                "SELECT DISTINCT u.provider_id,u.native_id::text FROM (" + ROLES + """
                    ) roles JOIN issue i ON i.id=roles.source_id
                    JOIN repository r ON r.id=i.repository_id
                    JOIN identity_provider p ON p.id=r.provider_id
                    JOIN "user" u ON u.id=roles.user_id
                    WHERE i.id=? AND (
                        ?
                        OR EXISTS (SELECT 1 FROM item a WHERE a.workspace_id=?
                            AND a.kind=? AND a.source_id=i.id)) AND ((?='scm.pull_request' AND i.issue_type='PULL_REQUEST')
                        OR (?='scm.issue' AND i.issue_type='ISSUE'))
                    """,
                (rs, row) -> new PersonIdentity(rs.getLong(1), Objects.requireNonNull(rs.getString(2)), null),
                sourceId,
                connected,
                workspaceId,
                itemKind,
                itemKind,
                itemKind);
    }

    private boolean connectedToWorkspace(long workspaceId, long sourceId) {
        var sources = jdbc.query(
                """
                SELECT r.native_id,p.type,p.server_url FROM issue i
                JOIN repository r ON r.id=i.repository_id JOIN identity_provider p ON p.id=r.provider_id
                WHERE i.id=?
                """,
                (rs, row) -> new RepositorySource(
                        rs.getLong(1), Objects.requireNonNull(rs.getString(2)), ScmOrigin.of(rs.getString(3))),
                sourceId);
        if (sources.isEmpty()) return false;
        var source = sources.getFirst();
        if (source.origin().isEmpty()) return false;
        return jdbc
                .query(
                        """
                        SELECT c.kind,c.config->>'serverUrl' FROM repository_to_monitor rm
                        JOIN connection c ON c.workspace_id=rm.workspace_id
                        WHERE rm.workspace_id=? AND rm.native_id=? AND c.kind=?
                        """,
                        (rs, row) -> PersonSourceNamespace.from(
                                        Objects.requireNonNull(rs.getString(1)), rs.getString(2))
                                .map(namespace ->
                                        ScmOrigin.of(namespace.serverUrl()).equals(source.origin()))
                                .orElse(false),
                        workspaceId,
                        source.nativeRepositoryId(),
                        source.type())
                .stream()
                .anyMatch(Boolean::booleanValue);
    }

    private record RepositorySource(long nativeRepositoryId, String type, Optional<String> origin) {}
}
