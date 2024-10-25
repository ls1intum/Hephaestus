package de.tum.in.www1.hephaestus.gitprovider.pullrequest;

import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.dto.PullRequestInfoDTO;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {

    Set<PullRequest> findByAuthor_Login(String authorLogin);

    @Query("""
            SELECT MIN(p.createdAt)
            FROM PullRequest p
            WHERE p.author.login = :authorLogin
            """)
    Optional<OffsetDateTime> firstContributionByAuthorLogin(@Param("authorLogin") String authorLogin);

    @Query("""
            SELECT new PullRequestInfoDTO(
                p.id,
                p.number,
                p.title,
                p.state,
                p.commentCount,
                new UserInfoDTO(p.author.id, p.author.login, p.author.avatarUrl, p.author.name, p.author.htmlUrl, p.author.createdAt, p.author.updatedAt),
                (SELECT new LabelInfoDTO(l.id, l.name, l.color) FROM Label l WHERE l MEMBER OF p.labels ORDER BY l.name),
                (SELECT new UserInfoDTO(u.id, u.login, u.avatarUrl, u.name, u.htmlUrl, u.createdAt, u.updatedAt) FROM User u WHERE u MEMBER OF p.assignees ORDER BY u.login),
                p.repository.nameWithOwner,
                p.additions,
                p.deletions,
                p.mergedAt,
                p.htmlUrl,
                p.createdAt,
                p.updatedAt)
            FROM PullRequest p
            WHERE (p.author.login = :assigneeLogin OR :assigneeLogin IN (SELECT u.login FROM p.assignees u)) AND p.state IN :states
            ORDER BY p.createdAt DESC
            """)
    List<PullRequestInfoDTO> findAssignedByLoginAndStates(
            @Param("assigneeLogin") String assigneeLogin,
            @Param("states") Set<PullRequest.State> states);
}