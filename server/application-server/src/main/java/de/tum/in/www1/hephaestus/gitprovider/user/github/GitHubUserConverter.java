package de.tum.in.www1.hephaestus.gitprovider.user.github;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntityConverter;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class GitHubUserConverter extends BaseGitServiceEntityConverter<GHUser, User> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubUserConverter.class);

    @Override
    public User convert(@NonNull GHUser source) {
        return update(source, new User());
    }

    @Override
    public User update(@NonNull GHUser source, @NonNull User user) {
        convertBaseFields(source, user);
        user.setLogin(source.getLogin());
        user.setAvatarUrl(source.getAvatarUrl());
        user.setDescription(source.getBio());
        user.setHtmlUrl(source.getHtmlUrl().toString());
        try {
            user.setName(source.getName() != null ? source.getName() : source.getLogin());
        } catch (IOException e) {
            logger.error("Failed to convert user name field for source {}: {}", source.getId(), e.getMessage());
            user.setName(source.getLogin());
        }
        try {
            user.setCompany(source.getCompany());
        } catch (IOException e) {
            logger.error("Failed to convert user company field for source {}: {}", source.getId(), e.getMessage());
        }
        try {
            user.setBlog(source.getBlog());
        } catch (IOException e) {
            logger.error("Failed to convert user blog field for source {}: {}", source.getId(), e.getMessage());
        }
        try {
            user.setLocation(source.getLocation());
        } catch (IOException e) {
            logger.error("Failed to convert user location field for source {}: {}", source.getId(), e.getMessage());
        }
        try {
            user.setEmail(source.getEmail());
        } catch (IOException e) {
            logger.error("Failed to convert user email field for source {}: {}", source.getId(), e.getMessage());
        }
        try {
            user.setType(convertUserType(source.getType()));
        } catch (IOException e) {
            logger.error("Failed to convert user type field for source {}: {}", source.getId(), e.getMessage());
        }
        try {
            user.setFollowers(source.getFollowersCount());
        } catch (IOException e) {
            logger.error("Failed to convert user followers field for source {}: {}", source.getId(), e.getMessage());
        }
        try {
            user.setFollowing(source.getFollowingCount());
        } catch (IOException e) {
            logger.error("Failed to convert user following field for source {}: {}", source.getId(), e.getMessage());
        }
        return user;
    }

    private User.Type convertUserType(String type) {
        switch (type) {
            case "User":
                return User.Type.USER;
            case "Organization":
                return User.Type.ORGANIZATION;
            case "Bot":
                return User.Type.BOT;
            default:
                return User.Type.USER;
        }
    }
}
