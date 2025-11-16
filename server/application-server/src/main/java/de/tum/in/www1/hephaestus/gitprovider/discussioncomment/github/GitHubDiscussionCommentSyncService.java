package de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github;

import de.tum.in.www1.hephaestus.gitprovider.discussion.Discussion;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionComment;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.Instant;
import org.kohsuke.github.GHRepositoryDiscussionComment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GitHubDiscussionCommentSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubDiscussionCommentSyncService.class);

    private final DiscussionCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final GitHubDiscussionCommentConverter commentConverter;
    private final GitHubUserConverter userConverter;

    public GitHubDiscussionCommentSyncService(
        DiscussionCommentRepository commentRepository,
        UserRepository userRepository,
        GitHubDiscussionCommentConverter commentConverter,
        GitHubUserConverter userConverter
    ) {
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.commentConverter = commentConverter;
        this.userConverter = userConverter;
    }

    @Transactional
    public DiscussionComment processDiscussionComment(GHRepositoryDiscussionComment ghComment, Discussion discussion) {
        if (ghComment == null) {
            return null;
        }

        var comment = commentRepository
            .findById(ghComment.getId())
            .map(existing -> updateIfNecessary(ghComment, existing))
            .orElseGet(() -> commentConverter.convert(ghComment));

        if (comment == null) {
            return null;
        }

        comment.setDiscussion(discussion);
        linkAuthor(comment, ghComment);
        comment.setLastSyncAt(Instant.now());
        return commentRepository.save(comment);
    }

    private DiscussionComment updateIfNecessary(GHRepositoryDiscussionComment source, DiscussionComment current) {
        try {
            var sourceUpdatedAt = source.getUpdatedAt();
            if (
                current.getUpdatedAt() == null ||
                (sourceUpdatedAt != null && current.getUpdatedAt().isBefore(sourceUpdatedAt))
            ) {
                return commentConverter.update(source, current);
            }
            return current;
        } catch (IOException e) {
            logger.warn("Failed to compare updatedAt for discussion comment {}: {}", source.getId(), e.getMessage());
            return commentConverter.update(source, current);
        }
    }

    private void linkAuthor(DiscussionComment comment, GHRepositoryDiscussionComment ghComment) {
        var ghAuthor = ghComment.getUser();
        if (ghAuthor == null) {
            comment.setAuthor(null);
            return;
        }

        var author = userRepository
            .findById(ghAuthor.getId())
            .orElseGet(() -> userRepository.save(userConverter.convert(ghAuthor)));
        comment.setAuthor(author);
    }
}
