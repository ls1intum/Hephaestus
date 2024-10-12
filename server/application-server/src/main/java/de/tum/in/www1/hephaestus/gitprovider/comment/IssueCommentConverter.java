package de.tum.in.www1.hephaestus.gitprovider.comment;

import org.kohsuke.github.GHIssueComment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.gitprovider.base.BaseGitServiceEntityConverter;

@Component
public class IssueCommentConverter extends BaseGitServiceEntityConverter<GHIssueComment, IssueComment> {

    protected static final Logger logger = LoggerFactory.getLogger(IssueCommentConverter.class);

    @Override
    public IssueComment convert(@NonNull GHIssueComment source) {
        return update(source, new IssueComment());
    }

    @Override
    public IssueComment update(@NonNull GHIssueComment source, @NonNull IssueComment comment) {
        convertBaseFields(source, comment);
        comment.setBody(source.getBody());
        return comment;
    }

}
