package de.tum.in.www1.hephaestus.codereview.actor;

import java.io.IOException;
import java.util.ArrayList;

import org.kohsuke.github.GHUser;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;

public class ActorConverter implements Converter<GHUser, Actor> {

    @Override
    public Actor convert(@NonNull GHUser source) {
        Actor actor = new Actor();
        actor.setLogin(source.getLogin());
        try {
            actor.setEmail(source.getEmail());
        } catch (IOException e) {
            actor.setEmail(null);
        }
        actor.setUrl(source.getHtmlUrl().toString());
        actor.setPullrequests(new ArrayList<>());
        actor.setComments(new ArrayList<>());
        return actor;
    }

}
