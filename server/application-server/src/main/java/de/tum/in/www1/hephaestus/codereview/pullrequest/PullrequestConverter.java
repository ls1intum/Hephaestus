package de.tum.in.www1.hephaestus.codereview.pullrequest;

import java.io.IOException;
import java.util.ArrayList;

import org.kohsuke.github.GHPullRequest;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
@ReadingConverter
public class PullrequestConverter implements Converter<GHPullRequest, Pullrequest> {
    @Override
    public Pullrequest convert(@NonNull GHPullRequest source) {
        Pullrequest pullrequest = new Pullrequest();
        pullrequest.setGithubId(source.getId());
        pullrequest.setTitle(source.getTitle());
        pullrequest.setUrl(source.getHtmlUrl().toString());
        pullrequest.setState(source.getState().toString());
        try {
            pullrequest.setCreatedAt(source.getCreatedAt().toString());
        } catch (IOException e) {
            pullrequest.setCreatedAt(null);
        }
        try {
            pullrequest.setUpdatedAt(source.getUpdatedAt().toString());
        } catch (IOException e) {
            pullrequest.setUpdatedAt(null);
        }
        pullrequest.setMergedAt(source.getMergedAt() != null ? source.getMergedAt().toString() : null);
        // set preliminary values to be filled in later
        pullrequest.setComments(new ArrayList<>());
        pullrequest.setAuthor(null);
        pullrequest.setRepository(null);
        return pullrequest;
    }

}
