package de.tum.in.www1.hephaestus.codereview.comment;

import java.io.IOException;

import org.kohsuke.github.GHIssueComment;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;

public class IssueCommentConverter implements Converter<GHIssueComment, IssueComment> {

    @Override
    public IssueComment convert(@NonNull GHIssueComment source) {
        IssueComment comment = new IssueComment();
        comment.setBody(source.getBody());
        comment.setGithubId(source.getId());
        try {
            comment.setCreatedAt(source.getCreatedAt().toString());
        } catch (IOException e) {
            comment.setCreatedAt(null);
        }
        try {
            comment.setUpdatedAt(source.getUpdatedAt().toString());
        } catch (IOException e) {
            comment.setUpdatedAt(null);
        }
        // set preliminary values to be filled in later
        comment.setPullrequest(null);
        comment.setAuthor(null);
        return comment;
    }

}
