package de.tum.in.www1.hephaestus.codereview.comment;

import java.io.IOException;

import org.kohsuke.github.GHIssueComment;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;

public class IssueCommentConverter implements Converter<GHIssueComment, IssueComment> {

    @Override
    public IssueComment convert(@NonNull GHIssueComment source) {
        Long id = source.getId();
        String body = source.getBody();

        IssueComment comment;
        try {
            String createdAt = source.getCreatedAt().toString();
            String updatedAt = source.getUpdatedAt().toString();
            comment = new IssueComment(id, body, createdAt, updatedAt);
        } catch (IOException e) {
            comment = new IssueComment(id, body, null, null);
        }
        return comment;
    }

}
