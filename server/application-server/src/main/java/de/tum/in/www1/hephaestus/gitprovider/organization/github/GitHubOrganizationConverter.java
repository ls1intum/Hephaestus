package de.tum.in.www1.hephaestus.gitprovider.organization.github;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntityConverter;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import java.io.IOException;
import org.kohsuke.github.GHOrganization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * @deprecated Use webhook DTOs and organization processing services instead.
 */
@Deprecated(forRemoval = true)
@SuppressWarnings("deprecation")
@Component
public class GitHubOrganizationConverter extends BaseGitServiceEntityConverter<GHOrganization, Organization> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubOrganizationConverter.class);

    @Override
    public Organization update(@NonNull GHOrganization source, @NonNull Organization organization) {
        convertBaseFields(source, organization);

        organization.setGithubId(source.getId());
        organization.setLogin(source.getLogin());
        try {
            organization.setName(source.getName());
        } catch (IOException e) {
            logger.info("Failed to get organization name for {}: {}", source.getLogin(), e.getMessage());
        }
        organization.setAvatarUrl(source.getAvatarUrl());
        if (source.getHtmlUrl() != null) {
            organization.setHtmlUrl(source.getHtmlUrl().toString());
        }
        return organization;
    }

    @Override
    public Organization convert(@NonNull GHOrganization source) {
        return update(source, new Organization());
    }
}
