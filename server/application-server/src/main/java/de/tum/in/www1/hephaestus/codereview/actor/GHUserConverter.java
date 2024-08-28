package de.tum.in.www1.hephaestus.codereview.actor;

import java.io.IOException;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;

public class GHUserConverter implements Converter<org.kohsuke.github.GHUser, GHUser> {

    @Override
    public GHUser convert(@NonNull org.kohsuke.github.GHUser source) {
        GHUser user = new GHUser();
        user.setLogin(source.getLogin());
        try {
            user.setEmail(source.getEmail());
        } catch (IOException e) {
            user.setEmail(null);
        }
        user.setUrl(source.getHtmlUrl().toString());
        return user;
    }

}
