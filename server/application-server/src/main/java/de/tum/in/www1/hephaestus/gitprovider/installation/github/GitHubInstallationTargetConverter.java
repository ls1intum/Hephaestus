package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntityConverter;
import de.tum.in.www1.hephaestus.gitprovider.installationtarget.InstallationTarget;
import de.tum.in.www1.hephaestus.gitprovider.installationtarget.InstallationTarget.TargetType;
import java.io.IOException;
import java.time.Instant;
import org.kohsuke.github.GHEventPayloadInstallationTarget;
import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class GitHubInstallationTargetConverter extends BaseGitServiceEntityConverter<GHUser, InstallationTarget> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubInstallationTargetConverter.class);

    @Override
    public InstallationTarget convert(@NonNull GHUser source) {
        return update(source, new InstallationTarget());
    }

    @Override
    public InstallationTarget update(@NonNull GHUser source, @NonNull InstallationTarget target) {
        convertBaseFields(source, target);
        target.setLogin(source.getLogin());
        target.setAvatarUrl(source.getAvatarUrl());
        target.setHtmlUrl(source.getHtmlUrl() != null ? source.getHtmlUrl().toString() : null);
        target.setNodeId(source.getNodeId());
        target.setType(TargetType.fromValue(readType(source)));
        target.setLastSyncedAt(Instant.now());

        Boolean siteAdmin = readSafely(source, "site_admin", source::isSiteAdmin);
        if (siteAdmin != null) {
            target.setSiteAdmin(siteAdmin);
        }
        target.setDescription(readSafely(source, "bio", source::getBio));

        Integer publicRepos = readSafely(source, "public_repo_count", source::getPublicRepoCount);
        if (publicRepos != null) {
            target.setPublicRepos(publicRepos);
        }
        Integer publicGists = readSafely(source, "public_gist_count", source::getPublicGistCount);
        if (publicGists != null) {
            target.setPublicGists(publicGists);
        }
        Integer followers = readSafely(source, "followers", source::getFollowersCount);
        if (followers != null) {
            target.setFollowers(followers);
        }
        Integer following = readSafely(source, "following", source::getFollowingCount);
        if (following != null) {
            target.setFollowing(following);
        }
        return target;
    }

    public InstallationTarget updateFromTargetPayload(
        GHEventPayloadInstallationTarget.Account account,
        InstallationTarget target
    ) {
        if (target.getId() == null) {
            target.setId(account.getId());
        }
        target.setLogin(account.getLogin());
        target.setNodeId(account.getNodeId());
        target.setAvatarUrl(account.getAvatarUrl());
        target.setHtmlUrl(account.getHtmlUrl());
        target.setDescription(account.getDescription());
        target.setVerified(account.getVerified());
        target.setHasOrganizationProjects(account.getHasOrganizationProjects());
        target.setHasRepositoryProjects(account.getHasRepositoryProjects());
        target.setPublicRepos(account.getPublicRepos());
        target.setPublicGists(account.getPublicGists());
        target.setFollowers(account.getFollowers());
        target.setFollowing(account.getFollowing());
        target.setArchivedAt(account.getArchivedAt());
        if (account.getCreatedAt() != null) {
            target.setCreatedAt(account.getCreatedAt());
        }
        if (account.getUpdatedAt() != null) {
            target.setUpdatedAt(account.getUpdatedAt());
        }
        target.setSiteAdmin(account.getSiteAdmin());
        target.setType(TargetType.fromValue(account.getType()));
        target.setLastSyncedAt(Instant.now());
        return target;
    }

    private String readType(GHUser source) {
        try {
            return source.getType();
        } catch (IOException e) {
            logger.error("Failed to read target type for target {}: {}", source.getId(), e.getMessage());
            return null;
        }
    }

    private <T> T readSafely(GHUser user, String field, IOSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IOException e) {
            logger.error("Failed to map {} for target {}: {}", field, user.getId(), e.getMessage());
            return null;
        }
    }

    @FunctionalInterface
    private interface IOSupplier<T> {
        T get() throws IOException;
    }
}
