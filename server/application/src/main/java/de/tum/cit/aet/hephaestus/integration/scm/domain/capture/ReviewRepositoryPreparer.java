package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import de.tum.cit.aet.hephaestus.core.security.ScmOrigin;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.RepositoryKey;
import de.tum.cit.aet.hephaestus.integration.scm.spi.ScmTokenSource;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

/** Authorizes the exact provider repository before Git credentials cross its boundary. */
@Component
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ReviewRepositoryPreparer {
    private final GitRepositoryManager git;
    private final PullRequestRepository pullRequests;
    private final RepositoryToMonitorRepository monitors;
    private final ConnectionService connections;
    private final List<ScmTokenSource> tokenSources;
    private final RepositoryRepository repositories;

    private record Authorized(RepositoryKey key, String cloneUrl, ScmTokenSource source, int number) {}

    public record PreparedReview(RepositoryKey key, String head, String target) {}

    @Transactional(readOnly = true)
    public RepositoryKey authorize(ScmCaptureContext capture) {
        return authorized(capture).key();
    }

    @Transactional(readOnly = true)
    public List<Repository> monitoredRepositories(long workspaceId) {
        return repositories.findAllByWorkspaceMonitors(workspaceId);
    }

    @Transactional(readOnly = true)
    public List<Repository> permittedRepositories(long workspaceId) {
        var kind = connections.findActiveProviderKind(workspaceId).orElse(null);
        if (kind == null) return List.of();
        var source = tokenSources.stream()
                .filter(candidate -> candidate.kind() == kind)
                .findFirst()
                .orElse(null);
        if (source == null) return List.of();
        var origin = source.serverUrl(workspaceId).flatMap(ScmOrigin::of);
        if (origin.isEmpty()) return List.of();
        return monitoredRepositories(workspaceId).stream()
                .filter(repository -> repository.getProvider().kind() == kind
                        && ScmOrigin.of(repository.getProvider().getServerUrl()).equals(origin))
                .toList();
    }

    private Authorized authorized(ScmCaptureContext capture) {
        var work = capture.work();
        long workspaceId = capture.workspaceId();
        var pullRequest = pullRequests
                .findByIdWithAuthorAndRepository(work.sourceId())
                .filter(pr -> pr.getDeletedAt() == null)
                .orElseThrow(() ->
                        new ItemCaptureException(ItemCaptureCode.ITEM_REMOVED, "Reviewed pull request is unavailable"));
        var repository = pullRequest.getRepository();
        if (repository == null
                || repository.getId() != work.repositoryId()
                || !repository.getNameWithOwner().equals(work.repositoryPath())
                || !monitors.existsByWorkspaceIdAndNameWithOwner(workspaceId, work.repositoryPath()))
            throw new ItemCaptureException(
                    ItemCaptureCode.ITEM_NOT_VISIBLE, "Workspace does not monitor the pinned repository");
        var integration = capture.pinned().integration();
        var connection = connections
                .findActive(workspaceId, integration.kind())
                .orElseThrow(() ->
                        new ItemCaptureException(ItemCaptureCode.ITEM_NOT_VISIBLE, "SCM connection is unavailable"));
        if (integration.workspaceId() != workspaceId
                || (integration.connectionId() != null
                        && !integration.connectionId().equals(connection.getId())))
            throw new ItemCaptureException(ItemCaptureCode.ITEM_NOT_VISIBLE, "SCM connection changed");
        var source = tokenSources.stream()
                .filter(candidate -> candidate.kind() == integration.kind())
                .findFirst()
                .orElseThrow(() -> new ItemCaptureException(
                        ItemCaptureCode.SOURCE_NOT_READY, "SCM credential source is unavailable"));
        String serverUrl = source.serverUrl(workspaceId)
                .orElseThrow(
                        () -> new ItemCaptureException(ItemCaptureCode.SOURCE_NOT_READY, "SCM server is unavailable"));
        if (repository.getProvider().kind() != integration.kind()
                || !ScmOrigin.of(serverUrl)
                        .equals(ScmOrigin.of(repository.getProvider().getServerUrl())))
            throw new ItemCaptureException(
                    ItemCaptureCode.ITEM_NOT_VISIBLE, "Repository belongs to another provider instance");
        String[] segments = work.repositoryPath().split("/", -1);
        if (segments.length < 2
                || Arrays.stream(segments)
                        .anyMatch(segment -> segment.isBlank() || segment.equals(".") || segment.equals("..")))
            throw new ItemCaptureException(ItemCaptureCode.CAPTURE_FAILED, "Repository has an invalid provider path");
        segments[segments.length - 1] += ".git";
        String cloneUrl = UriComponentsBuilder.fromUriString(serverUrl)
                .pathSegment(segments)
                .build()
                .encode()
                .toUriString();
        return new Authorized(new RepositoryKey(workspaceId, work.repositoryId()), cloneUrl, source, work.number());
    }

    public PreparedReview prepare(ScmCaptureContext capture) {
        var authorized = authorized(capture);
        String head = Objects.requireNonNull(capture.work().head(), "Pinned SCM work has no head");
        String base = Objects.requireNonNull(capture.work().base(), "Pinned SCM work has no base");
        if (!head.matches("[0-9a-f]{40}|[0-9a-f]{64}") || !base.matches("[0-9a-f]{40}|[0-9a-f]{64}"))
            throw new ItemCaptureException(
                    ItemCaptureCode.VERSION_UNAVAILABLE, "Pinned SCM version has invalid Git object ids");
        String token = authorized
                .source()
                .accessToken(capture.workspaceId())
                .orElseThrow(() ->
                        new ItemCaptureException(ItemCaptureCode.SOURCE_NOT_READY, "SCM credentials are unavailable"));
        var key = authorized.key();
        git.ensureRepository(key, authorized.cloneUrl(), token);
        if (!git.commitExists(key, head)) {
            authorized
                    .source()
                    .reviewHeadRef(authorized.number())
                    .ifPresent(ref -> git.fetchRemoteCommit(key, authorized.cloneUrl(), ref, head, token));
        }
        if (!git.commitExists(key, head) || !git.commitExists(key, base))
            throw new ItemCaptureException(
                    ItemCaptureCode.VERSION_UNAVAILABLE, "Pinned review diff range is unavailable");
        return new PreparedReview(key, head, base);
    }
}
