package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonSourceNamespace;
import de.tum.cit.aet.hephaestus.core.security.ScmOrigin;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.connection.SourceProviderNamespaces;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.RepositoryKey;
import de.tum.cit.aet.hephaestus.integration.scm.spi.ScmTokenSource;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class ScmRepositoryAccess {
    private final ConnectionService connections;
    private final RepositoryRepository repositories;
    private final List<ScmTokenSource> tokens;
    private final GitRepositoryManager git;

    public IntegrationRef authorize(long workspaceId, Repository repository) {
        var provider = repository.getProvider();
        if (provider.kind() != IntegrationKind.GITHUB && provider.kind() != IntegrationKind.GITLAB)
            throw unavailable("Repository does not belong to an SCM provider");
        if (!repositories.isMonitored(workspaceId, repository.getId()))
            throw unavailable("Workspace does not monitor this repository identity");
        var connection = connections
                .findActive(workspaceId, provider.kind())
                .orElseThrow(() -> unavailable("SCM connection is unavailable"));
        var origin = PersonSourceNamespace.from(
                        provider.kind().name(), SourceProviderNamespaces.configuredUrl(connection.getConfig()))
                .flatMap(namespace -> ScmOrigin.of(namespace.serverUrl()));
        if (origin.isEmpty() || !origin.equals(ScmOrigin.of(provider.getServerUrl())))
            throw unavailable("Repository belongs to another provider instance");
        return new IntegrationRef(provider.kind(), workspaceId, connection.getInstanceKey(), connection.getId());
    }

    public RepositoryKey prepare(long workspaceId, Repository repository) {
        var integration = authorize(workspaceId, repository);
        if (!git.isEnabled())
            throw new ItemCaptureException(ItemCaptureCode.SOURCE_NOT_READY, "Git capture is disabled");
        var sources =
                tokens.stream().filter(s -> s.kind() == integration.kind()).toList();
        if (sources.size() != 1)
            throw new ItemCaptureException(
                    ItemCaptureCode.SOURCE_NOT_READY, "SCM credentials are unavailable or ambiguous");
        var source = sources.getFirst();
        String origin = source.serverUrl(workspaceId).orElseThrow(() -> unavailable("SCM origin is unavailable"));
        if (!ScmOrigin.of(origin).equals(ScmOrigin.of(repository.getProvider().getServerUrl())))
            throw unavailable("SCM credential origin changed");
        String[] path = repository.getNameWithOwner().split("/", -1);
        if (path.length < 2 || Arrays.stream(path).anyMatch(p -> p.isBlank() || p.equals(".") || p.equals("..")))
            throw unavailable("Repository path is invalid");
        path[path.length - 1] += ".git";
        String clone = UriComponentsBuilder.fromUriString(origin)
                .pathSegment(path)
                .build()
                .encode()
                .toUriString();
        String token = source.accessToken(workspaceId)
                .orElseThrow(() ->
                        new ItemCaptureException(ItemCaptureCode.SOURCE_NOT_READY, "SCM credentials are unavailable"));
        RepositoryKey key = new RepositoryKey(workspaceId, repository.getId());
        git.ensureRepository(key, clone, token);
        return key;
    }

    private static ItemCaptureException unavailable(String reason) {
        return new ItemCaptureException(ItemCaptureCode.ITEM_NOT_VISIBLE, reason);
    }
}
