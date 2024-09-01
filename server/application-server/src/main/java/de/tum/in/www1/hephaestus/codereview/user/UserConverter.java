package de.tum.in.www1.hephaestus.codereview.user;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.codereview.base.BaseGitServiceEntityConverter;

@Service
public class UserConverter extends BaseGitServiceEntityConverter<org.kohsuke.github.GHUser, User> {

    protected static final Logger logger = LoggerFactory.getLogger(UserConverter.class);

    @Override
    public User convert(@NonNull org.kohsuke.github.GHUser source) {
        User user = new User();
        convertBaseFields(source, user);
        user.setLogin(source.getLogin());
        user.setUrl(source.getHtmlUrl().toString());
        user.setAvatarUrl(source.getAvatarUrl());
        try {
            user.setName(source.getName());
            user.setEmail(source.getEmail());
        } catch (IOException e) {
            logger.error("Failed to convert user fields for source {}: {}", source.getId(), e.getMessage());
        }
        return user;
    }

}
