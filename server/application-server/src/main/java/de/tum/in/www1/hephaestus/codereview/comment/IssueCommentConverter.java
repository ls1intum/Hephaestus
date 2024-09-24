package de.tum.in.www1.hephaestus.codereview.comment;

import java.io.IOException;

import org.kohsuke.github.GHIssueComment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.codereview.base.BaseGitServiceEntityConverter;

@Component
public class IssueCommentConverter extends BaseGitServiceEntityConverter<GHIssueComment, IssueComment> {

    protected static final Logger logger = LoggerFactory.getLogger(IssueCommentConverter.class);

    @Override
    public IssueComment convert(@NonNull GHIssueComment source) {
        IssueComment comment = new IssueComment();
        convertBaseFields(source, comment);
        comment.setBody(source.getBody());
        return comment;
    }

    @Override
    public IssueComment update(@NonNull GHIssueComment source, @NonNull IssueComment comment) {
        try {
            comment.setUpdatedAt(convertToOffsetDateTime(source.getUpdatedAt()));
        } catch (IOException e) {
            logger.error("Failed to convert updatedAt field for source {}: {}", source.getId(), e.getMessage());
        }
        comment.setBody(source.getBody());
        return comment;
    }

}
