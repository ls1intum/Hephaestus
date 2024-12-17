package de.tum.in.www1.hephaestus.gitprovider.issuecomment.github;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntityConverter;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubAuthorAssociationConverter;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import org.kohsuke.github.GHIssueComment;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class GitHubIssueCommentConverter extends BaseGitServiceEntityConverter<GHIssueComment, IssueComment> {

    private final GitHubAuthorAssociationConverter authorAssociationConverter;

    public GitHubIssueCommentConverter(GitHubAuthorAssociationConverter authorAssociationConverter) {
        this.authorAssociationConverter = authorAssociationConverter;
    }

    @Override
    public IssueComment convert(@NonNull GHIssueComment source) {
        return update(source, new IssueComment());
    }

    @Override
    public IssueComment update(@NonNull GHIssueComment source, @NonNull IssueComment comment) {
        convertBaseFields(source, comment);
        comment.setBody(source.getBody());
        comment.setHtmlUrl(source.getHtmlUrl().toString());
        comment.setAuthorAssociation(authorAssociationConverter.convert(source.getAuthorAssociation()));
        return comment;
    }
}
