package de.tum.in.www1.hephaestus.codereview.user;

import java.io.IOException;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;

public class UserConverter implements Converter<org.kohsuke.github.GHUser, User> {

    @Override
    public User convert(@NonNull org.kohsuke.github.GHUser source) {
        Long id = source.getId();
        String login = source.getLogin();
        String url = source.getHtmlUrl().toString();
        String avatarlUrl = source.getAvatarUrl();
        User user;
        try {
            String name = source.getName();
            String email = source.getEmail();
            String createdAt = source.getCreatedAt().toString();
            String updatedAt = source.getUpdatedAt().toString();
            user = new User(id, login, email, name, url, avatarlUrl, createdAt, updatedAt);
        } catch (IOException e) {
            user = new User(id, login, null, null, url, avatarlUrl, null, null);
        }
        return user;
    }

}
