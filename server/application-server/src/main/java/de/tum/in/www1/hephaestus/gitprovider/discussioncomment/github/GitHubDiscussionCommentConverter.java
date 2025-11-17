package de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntityConverter;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubAuthorAssociationConverter;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionComment;
import org.kohsuke.github.GHRepositoryDiscussionComment;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class GitHubDiscussionCommentConverter
    extends BaseGitServiceEntityConverter<GHRepositoryDiscussionComment, DiscussionComment> {

    private final GitHubAuthorAssociationConverter authorAssociationConverter;

    public GitHubDiscussionCommentConverter(GitHubAuthorAssociationConverter authorAssociationConverter) {
        this.authorAssociationConverter = authorAssociationConverter;
    }

    @Override
    public DiscussionComment convert(@NonNull GHRepositoryDiscussionComment source) {
        return update(source, new DiscussionComment());
    }

    @Override
    public DiscussionComment update(@NonNull GHRepositoryDiscussionComment source, @NonNull DiscussionComment target) {
        convertBaseFields(source, target);
        target.setBody(source.getBody());
        target.setParentCommentId(source.getParentId());
        if (source.getAuthorAssociation() != null) {
            target.setAuthorAssociation(authorAssociationConverter.convert(source.getAuthorAssociation()));
        }
        return target;
    }
}
