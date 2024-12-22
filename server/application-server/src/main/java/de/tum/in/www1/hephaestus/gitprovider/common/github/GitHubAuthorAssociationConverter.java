package de.tum.in.www1.hephaestus.gitprovider.common.github;

import de.tum.in.www1.hephaestus.gitprovider.common.AuthorAssociation;
import org.kohsuke.github.GHCommentAuthorAssociation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class GitHubAuthorAssociationConverter implements Converter<GHCommentAuthorAssociation, AuthorAssociation> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubAuthorAssociationConverter.class);

    @Override
    public AuthorAssociation convert(@NonNull GHCommentAuthorAssociation source) {
        switch (source) {
            case COLLABORATOR:
                return AuthorAssociation.COLLABORATOR;
            case CONTRIBUTOR:
                return AuthorAssociation.CONTRIBUTOR;
            case FIRST_TIME_CONTRIBUTOR:
                return AuthorAssociation.FIRST_TIME_CONTRIBUTOR;
            case FIRST_TIMER:
                return AuthorAssociation.FIRST_TIMER;
            case MEMBER:
                return AuthorAssociation.MEMBER;
            case NONE:
                return AuthorAssociation.NONE;
            case OWNER:
                return AuthorAssociation.OWNER;
            default:
                logger.error("Unknown author association: {}", source);
                return AuthorAssociation.NONE;
        }
    }
}
