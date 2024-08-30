package de.tum.in.www1.hephaestus.codereview.user;

import java.io.IOException;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;

public class UserConverter implements Converter<org.kohsuke.github.GHUser, User> {

    @Override
    public User convert(@NonNull org.kohsuke.github.GHUser source) {
        User user = new User();
        user.setLogin(source.getLogin());
        try {
            user.setName(source.getName());
        } catch (IOException e) {
            user.setName(null);
        }
        try {
            user.setEmail(source.getEmail());
        } catch (IOException e) {
            user.setEmail(null);
        }
        user.setUrl(source.getHtmlUrl().toString());
        user.setAvatarUrl(source.getAvatarUrl());
        return user;
    }

}
