package de.tum.in.www1.hephaestus.gitprovider.discussion.github;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntityConverter;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubAuthorAssociationConverter;
import de.tum.in.www1.hephaestus.gitprovider.discussion.Discussion;
import org.kohsuke.github.GHRepositoryDiscussion;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class GitHubDiscussionConverter extends BaseGitServiceEntityConverter<GHRepositoryDiscussion, Discussion> {

    private final GitHubAuthorAssociationConverter authorAssociationConverter;

    public GitHubDiscussionConverter(GitHubAuthorAssociationConverter authorAssociationConverter) {
        this.authorAssociationConverter = authorAssociationConverter;
    }

    @Override
    public Discussion convert(@NonNull GHRepositoryDiscussion source) {
        return update(source, new Discussion());
    }

    @Override
    public Discussion update(@NonNull GHRepositoryDiscussion source, @NonNull Discussion target) {
        convertBaseFields(source, target);
        target.setNumber(source.getNumber());
        target.setTitle(source.getTitle());
        target.setBody(source.getBody());
        target.setHtmlUrl(source.getHtmlUrl() != null ? source.getHtmlUrl().toString() : null);
        target.setCommentCount(source.getComments());
        target.setState(convertState(source));
        target.setLocked(source.isLocked());
        target.setActiveLockReason(source.getActiveLockReason());
        target.setAnswerChosenAt(source.getAnswerChosenAt());
        if (source.getAuthorAssociation() != null) {
            target.setAuthorAssociation(authorAssociationConverter.convert(source.getAuthorAssociation()));
        }

        return target;
    }

    private Discussion.State convertState(GHRepositoryDiscussion discussion) {
        if (discussion.getAnswerChosenAt() != null) {
            return Discussion.State.ANSWERED;
        }

        return switch (discussion.getState()) {
            case OPEN -> Discussion.State.OPEN;
            case LOCKED -> Discussion.State.LOCKED;
            default -> Discussion.State.UNKNOWN;
        };
    }
}
