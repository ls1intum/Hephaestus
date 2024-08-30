package de.tum.in.www1.hephaestus.codereview.comment;

import org.kohsuke.github.GHIssueComment;
import org.springframework.lang.NonNull;

import de.tum.in.www1.hephaestus.codereview.base.BaseGitServiceEntityConverter;

public class IssueCommentConverter extends BaseGitServiceEntityConverter<GHIssueComment, IssueComment> {

    @Override
    public IssueComment convert(@NonNull GHIssueComment source) {
        IssueComment comment = new IssueComment();
        convertBaseFields(source, comment);
        comment.setBody(source.getBody());
        return comment;
    }

}
