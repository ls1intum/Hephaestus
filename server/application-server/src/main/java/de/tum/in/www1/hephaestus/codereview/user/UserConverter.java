package de.tum.in.www1.hephaestus.codereview.user;

import java.io.IOException;

import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.codereview.base.BaseGitServiceEntityConverter;

@Component
public class UserConverter extends BaseGitServiceEntityConverter<org.kohsuke.github.GHUser, User> {

    protected static final Logger logger = LoggerFactory.getLogger(UserConverter.class);

    @Override
    public User convert(@NonNull GHUser source) {
        return update(source, new User());
    }

    @Override
    public User update(@NonNull GHUser source, @NonNull User user) {
        convertBaseFields(source, user);
        user.setLogin(source.getLogin());
        user.setUrl(source.getHtmlUrl().toString());
        user.setAvatarUrl(source.getAvatarUrl());
        try {
            user.setEmail(source.getEmail());
        } catch (IOException e) {
            logger.error("Failed to convert user email field for source {}: {}", source.getId(), e.getMessage());
        }
        try {
            user.setName(source.getName() != null ? source.getName() : source.getLogin());
        } catch (IOException e) {
            logger.error("Failed to convert user name field for source {}: {}", source.getId(), e.getMessage());
        }
        try {
            user.setType(convertUserType(source.getType()));
        } catch (IOException e) {
            logger.error("Failed to convert user type field for source {}: {}", source.getId(), e.getMessage());
        }
        return user;
    }

    private UserType convertUserType(String type) {
        switch (type) {
            case "User":
                return UserType.USER;
            case "Bot":
                return UserType.BOT;
            default:
                return UserType.USER;
        }
    }

}
