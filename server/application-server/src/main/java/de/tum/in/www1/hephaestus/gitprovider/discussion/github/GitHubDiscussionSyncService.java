package de.tum.in.www1.hephaestus.gitprovider.discussion.github;

import de.tum.in.www1.hephaestus.gitprovider.discussion.Discussion;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionCategoryService;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github.GitHubDiscussionCommentSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.kohsuke.github.GHException;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepositoryDiscussion;
import org.kohsuke.github.GHRepositoryDiscussionsSupport;
import org.kohsuke.github.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GitHubDiscussionSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubDiscussionSyncService.class);

    private final DiscussionRepository discussionRepository;
    private final RepositoryRepository repositoryRepository;
    private final UserRepository userRepository;
    private final GitHubDiscussionConverter discussionConverter;
    private final GitHubUserConverter userConverter;
    private final GitHubDiscussionCommentSyncService discussionCommentSyncService;
    private final DiscussionCategoryService discussionCategoryService;
    private final DiscussionCommentRepository discussionCommentRepository;

    private static final Pattern ANSWER_COMMENT_ANCHOR = Pattern.compile("discussioncomment-(?<id>\\d+)");

    public GitHubDiscussionSyncService(
        DiscussionRepository discussionRepository,
        RepositoryRepository repositoryRepository,
        UserRepository userRepository,
        GitHubDiscussionConverter discussionConverter,
        GitHubUserConverter userConverter,
        GitHubDiscussionCommentSyncService discussionCommentSyncService,
        DiscussionCategoryService discussionCategoryService,
        DiscussionCommentRepository discussionCommentRepository
    ) {
        this.discussionRepository = discussionRepository;
        this.repositoryRepository = repositoryRepository;
        this.userRepository = userRepository;
        this.discussionConverter = discussionConverter;
        this.userConverter = userConverter;
        this.discussionCommentSyncService = discussionCommentSyncService;
        this.discussionCategoryService = discussionCategoryService;
        this.discussionCommentRepository = discussionCommentRepository;
    }

    @Transactional
    public Instant syncRecentDiscussions(GHRepository repository, Instant since) {
        Instant newestTimestamp = since;
        var iterator = fetchDiscussions(repository, since).iterator();
        try {
            while (iterator.hasNext()) {
                GHRepositoryDiscussion ghDiscussion = iterator.next();
                Instant previousSync = discussionRepository
                    .findById(ghDiscussion.getId())
                    .map(Discussion::getLastSyncAt)
                    .orElse(null);
                var discussion = processDiscussion(ghDiscussion, repository);
                if (discussion == null) {
                    continue;
                }
                Instant commentCutoff = moreRecent(previousSync, since);
                syncDiscussionComments(repository, ghDiscussion, discussion, commentCutoff);
                linkAnswerComment(discussion, ghDiscussion);
                newestTimestamp = mostRecent(newestTimestamp, safeUpdatedAt(ghDiscussion));
            }
        } catch (GHException ghException) {
            if (discussionsDisabled(ghException)) {
                logger.debug(
                    "Repository {} reports discussions disabled ({}); skipping discussion sync.",
                    repository.getFullName(),
                    ghException.getMessage()
                );
                return Optional.ofNullable(newestTimestamp).orElse(since);
            }
            throw ghException;
        }
        return Optional.ofNullable(newestTimestamp).orElseGet(Instant::now);
    }

    @Transactional
    public Discussion processDiscussion(GHRepositoryDiscussion ghDiscussion, GHRepository ghRepository) {
        var discussion = discussionRepository
            .findById(ghDiscussion.getId())
            .map(existing -> updateIfNecessary(ghDiscussion, existing))
            .orElseGet(() -> discussionConverter.convert(ghDiscussion));

        if (discussion == null) {
            return null;
        }

        var repository = linkRepository(discussion, ghRepository);
        linkCategory(discussion, ghDiscussion, repository);
        linkAuthor(discussion, ghDiscussion);
        linkAnswerSelector(discussion, ghDiscussion);
        linkAnswerComment(discussion, ghDiscussion);

        discussion.setLastSyncAt(Instant.now());
        return discussionRepository.save(discussion);
    }

    private void syncDiscussionComments(
        GHRepository repository,
        GHRepositoryDiscussion ghDiscussion,
        Discussion discussion,
        Instant since
    ) {
        try {
            GHRepositoryDiscussionsSupport.listDiscussionComments(repository, ghDiscussion.getNumber(), since).forEach(
                ghComment -> discussionCommentSyncService.processDiscussionComment(ghComment, discussion)
            );
        } catch (GHFileNotFoundException notFound) {
            logger.debug(
                "Discussion {} no longer exposes comments ({}); skipping",
                ghDiscussion.getId(),
                notFound.getMessage()
            );
        } catch (IOException fetchError) {
            if (discussionsDisabled(fetchError)) {
                logger.debug(
                    "Skipping comments for discussion {} because discussions are disabled: {}",
                    ghDiscussion.getId(),
                    fetchError.getMessage()
                );
                return;
            }
            logger.warn(
                "Failed to backfill comments for discussion {}: {}",
                ghDiscussion.getId(),
                fetchError.getMessage()
            );
        } catch (GHException ghException) {
            if (discussionsDisabled(ghException)) {
                logger.debug(
                    "Skipping comments for discussion {} because discussions are disabled: {}",
                    ghDiscussion.getId(),
                    ghException.getMessage()
                );
                return;
            }
            throw ghException;
        }
    }

    private Iterable<GHRepositoryDiscussion> fetchDiscussions(GHRepository repository, Instant since) {
        try {
            return GHRepositoryDiscussionsSupport.listDiscussions(repository, since);
        } catch (GHFileNotFoundException notFound) {
            logger.debug(
                "Repository {} has discussions disabled; skipping backfill: {}",
                repository.getFullName(),
                notFound.getMessage()
            );
            return Collections.emptyList();
        } catch (IOException fetchError) {
            logger.warn(
                "Failed to list discussions for repository {}: {}",
                repository.getFullName(),
                fetchError.getMessage()
            );
            return Collections.emptyList();
        }
    }

    private Instant safeUpdatedAt(GHRepositoryDiscussion discussion) {
        try {
            return discussion.getUpdatedAt();
        } catch (IOException e) {
            logger.debug("Failed to read updatedAt for discussion {}: {}", discussion.getId(), e.getMessage());
            return null;
        }
    }

    private Instant moreRecent(Instant candidate, Instant baseline) {
        if (candidate == null) {
            return baseline;
        }
        if (baseline == null || candidate.isAfter(baseline)) {
            return candidate;
        }
        return baseline;
    }

    private Instant mostRecent(Instant current, Instant candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.isAfter(current)) {
            return candidate;
        }
        return current;
    }

    private boolean discussionsDisabled(Exception exception) {
        Throwable cursor = exception;
        while (cursor != null) {
            if (cursor instanceof HttpException httpException) {
                int status = httpException.getResponseCode();
                if (status == 404 || status == 410) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private Discussion updateIfNecessary(GHRepositoryDiscussion source, Discussion current) {
        try {
            var sourceUpdatedAt = source.getUpdatedAt();
            if (
                current.getUpdatedAt() == null ||
                (sourceUpdatedAt != null && current.getUpdatedAt().isBefore(sourceUpdatedAt))
            ) {
                return discussionConverter.update(source, current);
            }
            return current;
        } catch (IOException e) {
            logger.warn("Failed to compare updatedAt for discussion {}: {}", source.getId(), e.getMessage());
            return discussionConverter.update(source, current);
        }
    }

    private Repository linkRepository(Discussion discussion, GHRepository ghRepository) {
        var repository = Optional.ofNullable(ghRepository)
            .flatMap(repo -> repositoryRepository.findById(repo.getId()))
            .or(() ->
                Optional.ofNullable(ghRepository)
                    .map(GHRepository::getFullName)
                    .flatMap(repositoryRepository::findByNameWithOwner)
            )
            .orElse(null);
        discussion.setRepository(repository);
        return repository;
    }

    private void linkAuthor(Discussion discussion, GHRepositoryDiscussion ghDiscussion) {
        var ghAuthor = ghDiscussion.getUser();
        if (ghAuthor == null) {
            discussion.setAuthor(null);
            return;
        }

        var author = userRepository
            .findById(ghAuthor.getId())
            .orElseGet(() -> userRepository.save(userConverter.convert(ghAuthor)));
        discussion.setAuthor(author);
    }

    private void linkAnswerSelector(Discussion discussion, GHRepositoryDiscussion ghDiscussion) {
        var ghUser = ghDiscussion.getAnswerChosenBy();
        if (ghUser == null) {
            discussion.setAnswerChosenBy(null);
            return;
        }

        User selector = userRepository
            .findById(ghUser.getId())
            .orElseGet(() -> userRepository.save(userConverter.convert(ghUser)));
        discussion.setAnswerChosenBy(selector);
    }

    private void linkCategory(Discussion discussion, GHRepositoryDiscussion ghDiscussion, Repository repository) {
        var ghCategory = ghDiscussion.getCategory();
        if (ghCategory == null || repository == null) {
            discussion.setCategory(null);
            return;
        }
        discussion.setCategory(discussionCategoryService.upsertCategory(ghCategory, repository));
    }

    private void linkAnswerComment(Discussion discussion, GHRepositoryDiscussion ghDiscussion) {
        Long answerCommentId = extractAnswerCommentId(ghDiscussion);
        if (answerCommentId == null) {
            discussion.setAnswerComment(null);
            return;
        }
        discussionCommentRepository
            .findById(answerCommentId)
            .ifPresentOrElse(discussion::setAnswerComment, () -> discussion.setAnswerComment(null));
    }

    private Long extractAnswerCommentId(GHRepositoryDiscussion ghDiscussion) {
        var url = ghDiscussion.getAnswerHtmlUrl();
        if (url == null) {
            return null;
        }
        var ref = url.getRef();
        if (ref == null) {
            return null;
        }
        Matcher matcher = ANSWER_COMMENT_ANCHOR.matcher(ref);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.valueOf(matcher.group("id"));
        } catch (NumberFormatException ex) {
            logger.debug("Failed to parse answer comment id from {}", ref, ex);
            return null;
        }
    }
}
