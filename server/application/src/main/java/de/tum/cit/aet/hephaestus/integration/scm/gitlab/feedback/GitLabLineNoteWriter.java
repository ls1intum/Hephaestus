package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import static de.tum.cit.aet.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitLabMrResolver.GRAPHQL_TIMEOUT;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressGateway;
import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressGuard;
import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressSuppressedException;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyDisposition;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyForm;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyReceipt;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyUpdateKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyUpdateReceipt;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyWriteFence;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyWriteResult;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackAnchor;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackTarget;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.integration.core.spi.LineNote;
import de.tum.cit.aet.hephaestus.integration.core.spi.LineNoteWriter;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmVersion;
import de.tum.cit.aet.hephaestus.integration.scm.feedback.ScmFeedbackFormatter;
import de.tum.cit.aet.hephaestus.integration.scm.feedback.ScmFeedbackTargetResolver;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabTokenService;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitLabMrResolver.MrCoordinates;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitLabMrResolver.MrInfo;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.stereotype.Component;

// Fence each physical copy before creating it, and preserve its marker during correction.
@Component
@ConditionalOnServerRole
@OutboundEgressGateway
@ConditionalOnProperty(name = "hephaestus.integration.gitlab.enabled", havingValue = "true", matchIfMissing = false)
public class GitLabLineNoteWriter implements LineNoteWriter {

    private static final Logger log = LoggerFactory.getLogger(GitLabLineNoteWriter.class);

    private static final int DISCUSSIONS_PAGE_SIZE = 100;

    private static final int MAX_DISCUSSION_PAGES = 50;

    private static final Pattern COMMIT_SHA = Pattern.compile("[0-9a-fA-F]{7,64}");

    private final GitLabGraphQlClientProvider gitLabProvider;
    private final GitLabMrResolver mrResolver;
    private final GitLabTokenService tokenService;
    private final GitLabDiffVersionReader diffVersions;
    private final OutboundEgressGuard egressGuard;
    private final ScmFeedbackTargetResolver targets;

    public GitLabLineNoteWriter(
            GitLabGraphQlClientProvider gitLabProvider,
            GitLabMrResolver mrResolver,
            GitLabTokenService tokenService,
            OutboundEgressGuard egressGuard,
            ScmFeedbackTargetResolver targets,
            GitLabDiffVersionReader diffVersions) {
        this.gitLabProvider = gitLabProvider;
        this.mrResolver = mrResolver;
        this.tokenService = tokenService;
        this.egressGuard = egressGuard;
        this.targets = targets;
        this.diffVersions = diffVersions;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public CopyWriteResult postImmutablePackage(
            FeedbackTarget target, List<LineNote> feedbackItems, CopyWriteFence fence) {
        if (feedbackItems.isEmpty()) {
            return CopyWriteResult.of(List.of());
        }
        long scopeId = target.integration().workspaceId();
        String revision;
        try {
            revision = ScmVersion.reviewedHead(target.version());
        } catch (IllegalArgumentException unavailable) {
            return CopyWriteResult.of(notSent(feedbackItems));
        }
        boolean rateLimited = gitLabProvider.isRateLimitCritical(scopeId);
        if (rateLimited || revision == null || !COMMIT_SHA.matcher(revision).matches()) {
            // Without the reviewed commit no comment can link to the code the review read.
            log.warn(
                    "GitLab location comments not requested: workspaceId={}, rateLimited={}, reviewedRevision={}",
                    scopeId,
                    rateLimited,
                    sanitizeForLog(revision));
            return CopyWriteResult.of(notSent(feedbackItems));
        }
        MrCoordinates mr = GitLabMrResolver.parseSubjectExternalId(targets.subject(target, IntegrationKind.GITLAB));
        MrInfo mrInfo = mrResolver.resolve(scopeId, mr.projectPath(), mr.iid());
        String projectUrl = projectUrl(scopeId, mr);
        Map<String, Copy> copies = indexCopies(scopeId, mr, feedbackItems, target.version(), projectUrl);

        var pinnedDiff = diffVersions.read(scopeId, mr, target.version());
        List<CopyReceipt> completed = new ArrayList<>(feedbackItems.size());
        Set<String> processedKeys = new HashSet<>();
        for (int index = 0; index < feedbackItems.size(); index++) {
            LineNote item = feedbackItems.get(index);
            String key = item.writeKey();
            if (key != null && !processedKeys.add(key)) {
                continue;
            }
            FeedbackAnchor diff = item.anchor();
            if (item.body().isBlank() || (diff.endLine() != null && diff.side() == null)) {
                completed.add(CopyReceipt.notSent(item.copyId()));
                continue;
            }
            Copy copy = copies.get(key);
            if (copy != null) {
                // A copy that does not verify proves neither delivery nor absence: nothing is
                // created next to it.
                completed.add(copy.verified() ? copy.preserved(item) : CopyReceipt.notSent(item.copyId()));
                continue;
            }
            String anchorVersion =
                    diff.side() == CitationSide.OLD ? target.version().baseVersion() : revision;
            if (anchorVersion == null || !COMMIT_SHA.matcher(anchorVersion).matches()) {
                completed.add(CopyReceipt.notSent(item.copyId()));
                continue;
            }
            List<LineNote> rest = feedbackItems.subList(index, feedbackItems.size());
            try {
                egressGuard.requireDeliveryAllowed("gitlab.post-inline-feedback");
            } catch (OutboundEgressSuppressedException e) {
                completed.addAll(unrequested(rest, copies));
                return CopyWriteResult.withheld(completed, deliveryKeys(rest));
            }
            if (!fence.beforeCreate(List.of(item.copyId()), List.copyOf(completed))) {
                completed.addAll(unrequested(rest, copies));
                return CopyWriteResult.of(completed);
            }
            var position = pinnedDiff == null ? null : GitLabDiffPositions.build(pinnedDiff, diff);
            Attempt attempt = position == null ? null : createDiffNote(scopeId, mrInfo.globalId(), item, position);
            if (attempt == null) {
                // The committed copy fence covers the fallback after a confirmed position rejection.
                if (position != null) {
                    try {
                        egressGuard.requireDeliveryAllowed("gitlab.post-inline-feedback");
                    } catch (OutboundEgressSuppressedException e) {
                        completed.addAll(unrequested(rest, copies));
                        return CopyWriteResult.withheld(completed, deliveryKeys(rest));
                    }
                }
                attempt = createLocationComment(
                        scopeId,
                        mrInfo.globalId(),
                        locationBody(projectUrl, anchorVersion, diff, item),
                        diff,
                        item.copyId());
            }
            completed.add(attempt.receipt());
            if (attempt.stop()) {
                completed.addAll(unrequested(feedbackItems.subList(index + 1, feedbackItems.size()), copies));
                break;
            }
        }
        return CopyWriteResult.of(completed);
    }

    @Override
    public @Nullable List<CopyReceipt> findPosted(FeedbackTarget target, List<LineNote> feedbackItems) {
        if (feedbackItems.isEmpty()) {
            return List.of();
        }
        long scopeId = target.integration().workspaceId();
        if (gitLabProvider.isRateLimitCritical(scopeId)
                || feedbackItems.stream().anyMatch(item -> item.writeKey() == null)) {
            return null;
        }
        try {
            MrCoordinates mr = GitLabMrResolver.parseSubjectExternalId(targets.subject(target, IntegrationKind.GITLAB));
            Map<String, Copy> copies =
                    indexCopies(scopeId, mr, feedbackItems, target.version(), projectUrl(scopeId, mr));
            List<CopyReceipt> found = new ArrayList<>();
            for (LineNote item : feedbackItems) {
                Copy copy = copies.get(item.writeKey());
                if (copy != null && copy.verified()) {
                    found.add(copy.preserved(item));
                }
            }
            return found;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // Only an explicit position rejection proves that a fallback cannot duplicate a created note.
    private @Nullable Attempt createDiffNote(long scopeId, String mrId, LineNote item, Map<String, Object> position) {
        try {
            var response = gitLabProvider
                    .forScope(scopeId)
                    .documentName("CreateDiffNote")
                    .variable("noteableId", mrId)
                    .variable("body", postedBody(item))
                    .variable("position", position)
                    .execute()
                    .block(GRAPHQL_TIMEOUT);
            Map<String, Object> payload =
                    response == null ? null : response.field("createDiffNote").getValue();
            if (payload != null
                    && payload.get("note") instanceof Map<?, ?> note
                    && note.get("id") instanceof String id
                    && !id.isBlank()) {
                return new Attempt(
                        new CopyReceipt(
                                item.copyId(),
                                CopyDisposition.POSTED,
                                id,
                                note.get("url") instanceof String url ? url : null,
                                true,
                                CopyForm.LINE_NOTE),
                        false);
            }
            if (response != null
                    && response.getErrors().isEmpty()
                    && payload != null
                    && payload.containsKey("note")
                    && payload.get("note") == null
                    && payload.get("errors") instanceof List<?> errors
                    && !errors.isEmpty()
                    && errors.stream().allMatch(GitLabLineNoteWriter::positionError)) return null;
            return new Attempt(CopyReceipt.attempted(item.copyId()), false);
        } catch (RuntimeException failure) {
            return new Attempt(CopyReceipt.attempted(item.copyId()), isRateLimitError(failure));
        }
    }

    private static boolean positionError(Object error) {
        if (!(error instanceof String text)) return false;
        String message = text.toLowerCase(Locale.ROOT);
        return message.contains("position")
                || message.contains("line code")
                || message.contains("line_code")
                || message.contains("diff refs")
                || message.contains("diff_refs");
    }

    // A failed ordinary-note request can still create a comment; it cannot prove rejection before the write.
    private Attempt createLocationComment(
            long scopeId, String mrGlobalId, String body, FeedbackAnchor diff, UUID copyId) {
        ClientGraphQlResponse response;
        try {
            response = gitLabProvider
                    .forScope(scopeId)
                    .documentName("CreateMergeRequestNote")
                    .variable("noteableId", mrGlobalId)
                    .variable("body", body)
                    .execute()
                    .block(GRAPHQL_TIMEOUT);
        } catch (Exception e) {
            log.warn(
                    "GitLab location comment outcome unknown: workspaceId={}, file={}, line={}",
                    scopeId,
                    sanitizeForLog(diff.path()),
                    diff.endLine(),
                    e);
            return new Attempt(CopyReceipt.attempted(copyId), isRateLimitError(e));
        }
        Map<String, Object> payload =
                response == null ? null : response.field("createNote").getValue();
        if (payload != null
                && payload.get("note") instanceof Map<?, ?> created
                && created.get("id") instanceof String noteId
                && !noteId.isBlank()) {
            String url = created.get("url") instanceof String noteUrl ? noteUrl : null;
            return new Attempt(
                    new CopyReceipt(copyId, CopyDisposition.POSTED, noteId, url, true, CopyForm.FILE_NOTE), false);
        }
        log.warn(
                "GitLab location comment returned no note: workspaceId={}, file={}, line={}",
                scopeId,
                sanitizeForLog(diff.path()),
                diff.endLine());
        return new Attempt(CopyReceipt.attempted(copyId), false);
    }

    // Partial discussions, missing identity, or incomplete pagination cannot prove a copy absent.
    private Map<String, Copy> indexCopies(
            long scopeId, MrCoordinates mr, List<LineNote> items, ItemVersion version, String projectUrl) {
        Map<String, LineNote> expected = new HashMap<>();
        for (LineNote item : items) {
            expected.putIfAbsent(item.writeKey(), item);
        }
        Map<String, Copy> copies = new LinkedHashMap<>();
        try {
            String cursor = null;
            for (int page = 1; ; page++) {
                ClientGraphQlResponse response = gitLabProvider
                        .forScope(scopeId)
                        .documentName("GetMergeRequestDiscussions")
                        .variable("fullPath", mr.projectPath())
                        .variable("iid", String.valueOf(mr.iid()))
                        .variable("first", DISCUSSIONS_PAGE_SIZE)
                        .variable("after", cursor)
                        .execute()
                        .block(GRAPHQL_TIMEOUT);
                if (response == null || !response.getErrors().isEmpty()) {
                    throw new FeedbackDeliveryException("GitLab discussion lookup returned no answer");
                }
                String currentUserId = response.field("currentUser.id").getValue();
                Map<String, Object> connection =
                        response.field("project.mergeRequest.discussions").getValue();
                if (currentUserId == null
                        || currentUserId.isBlank()
                        || connection == null
                        || !(connection.get("nodes") instanceof List<?> discussions)
                        || !(connection.get("pageInfo") instanceof Map<?, ?> pageInfo)) {
                    throw new FeedbackDeliveryException("GitLab discussion lookup was incomplete");
                }
                for (Object discussion : discussions) {
                    indexDiscussion(discussion, currentUserId, version, projectUrl, expected, copies);
                }
                Object hasNextPage = pageInfo.get("hasNextPage");
                if (Boolean.FALSE.equals(hasNextPage)) {
                    return copies;
                }
                if (!Boolean.TRUE.equals(hasNextPage)
                        || page == MAX_DISCUSSION_PAGES
                        || !(pageInfo.get("endCursor") instanceof String next)
                        || next.isBlank()
                        || next.equals(cursor)) {
                    throw new FeedbackDeliveryException("GitLab discussion pagination did not reach its end");
                }
                cursor = next;
            }
        } catch (FeedbackDeliveryException e) {
            throw e;
        } catch (Exception e) {
            throw new FeedbackDeliveryException("GitLab discussion lookup was inconclusive", e);
        }
    }

    private static void indexDiscussion(
            @Nullable Object node,
            String currentUserId,
            ItemVersion version,
            String projectUrl,
            Map<String, LineNote> expected,
            Map<String, Copy> copies) {
        if (!(node instanceof Map<?, ?> discussion)
                || !(discussion.get("notes") instanceof Map<?, ?> connection)
                || !(connection.get("nodes") instanceof List<?> notes)
                || !(connection.get("pageInfo") instanceof Map<?, ?> pageInfo)
                || !Boolean.FALSE.equals(pageInfo.get("hasNextPage"))) {
            throw new FeedbackDeliveryException("A GitLab discussion was not read completely");
        }
        String discussionId = discussion.get("id") instanceof String id ? id : null;
        for (Object entry : notes) {
            if (!(entry instanceof Map<?, ?> note)
                    || !(note.get("body") instanceof String body)
                    || !(note.get("id") instanceof String noteId)
                    || noteId.isBlank()) {
                throw new FeedbackDeliveryException("A GitLab note was not read completely");
            }
            if (Boolean.TRUE.equals(note.get("system"))) continue;
            String key = ScmFeedbackFormatter.lastDeliveryKey(body);
            if (key == null) {
                continue;
            }
            LineNote item = expected.get(key);
            CopyForm copyForm = item != null
                            && note.get("author") instanceof Map<?, ?> author
                            && currentUserId.equals(author.get("id"))
                    ? copyForm(note, body, item, version, projectUrl)
                    : null;
            String url = note.get("url") instanceof String noteUrl ? noteUrl : null;
            Copy previous = copies.get(key);
            if (copyForm != null && (previous == null || !previous.verified())) {
                copies.put(key, new Copy(true, noteId, discussionId, url, copyForm));
            } else if (copyForm == null && previous == null) {
                copies.put(key, new Copy(false, noteId, discussionId, url, null));
            }
        }
    }

    private static @Nullable CopyForm copyForm(
            Map<?, ?> note, String body, LineNote item, ItemVersion version, String projectUrl) {
        FeedbackAnchor diff = item.anchor();
        String revision = diff.side() == CitationSide.OLD ? version.baseVersion() : ScmVersion.reviewedHead(version);
        if (diff.endLine() != null && diff.side() == null) return null;
        // Only a position the response answered with null proves an ordinary comment; a missing one
        // proves nothing.
        boolean unpositioned = note.containsKey("position") && note.get("position") == null;
        if (unpositioned
                && ((revision != null && body.equals(locationBody(projectUrl, revision, diff, item)))
                        || (diff.endLine() != null && body.equals(fallbackBody(diff, item))))) {
            return CopyForm.FILE_NOTE;
        }
        boolean lineCopy = diff.endLine() != null
                && revision != null
                && body.equals(postedBody(item))
                && note.get("position") instanceof Map<?, ?> position
                && diff.path().equals(position.get(diff.side() == CitationSide.OLD ? "oldPath" : "newPath"))
                && position.get(diff.side() == CitationSide.OLD ? "oldLine" : "newLine") instanceof Number line
                && line.intValue() == diff.endLine()
                && position.get("diffRefs") instanceof Map<?, ?> diffRefs
                && Objects.equals(version.baseVersion(), diffRefs.get("baseSha"))
                && Objects.equals(ScmVersion.reviewedHead(version), diffRefs.get("headSha"))
                && diffRefs.get("startSha") instanceof String startSha
                && COMMIT_SHA.matcher(startSha).matches();
        return lineCopy ? CopyForm.LINE_NOTE : null;
    }

    // Stopping a package must preserve receipts for copies already verified at the provider.
    private static List<CopyReceipt> unrequested(List<LineNote> items, Map<String, Copy> copies) {
        return items.stream()
                .map(item -> {
                    Copy copy = item.writeKey() == null ? null : copies.get(item.writeKey());
                    return copy != null && copy.verified() ? copy.preserved(item) : CopyReceipt.notSent(item.copyId());
                })
                .toList();
    }

    private static List<CopyReceipt> notSent(List<LineNote> items) {
        return items.stream().map(item -> CopyReceipt.notSent(item.copyId())).toList();
    }

    private static List<UUID> deliveryKeys(List<LineNote> feedbackItems) {
        return feedbackItems.stream().map(LineNote::copyId).toList();
    }

    // Encode each project segment independently so namespace slashes retain their structure.
    private String projectUrl(long scopeId, MrCoordinates mr) {
        String serverUrl = tokenService.resolveServerUrl(scopeId);
        String base = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        return base + "/" + encodedPath(mr.projectPath());
    }

    private static String postedBody(LineNote item) {
        String body = GitLabSummaryWriter.escapeSlashCommands(item.body());
        body = body + "\n" + item.writeMarker();
        String key = item.writeKey();
        return body + "\n" + ScmFeedbackFormatter.deliveryTag(key);
    }

    // File names stay inside the encoded URL; link text contains only line coordinates.
    private static String locationBody(String projectUrl, String revision, FeedbackAnchor diff, LineNote item) {
        if (diff.endLine() == null)
            return "**[View file]("
                    + projectUrl
                    + "/-/blob/"
                    + revision
                    + "/"
                    + encodedPath(diff.path())
                    + ")**\n\n"
                    + postedBody(item);
        Integer start = diff.startLine();
        boolean range = start != null && start < diff.endLine();
        String lines = range ? start + "-" + diff.endLine() : String.valueOf(diff.endLine());
        String label =
                range ? "View code at lines " + start + "–" + diff.endLine() : "View code at line " + diff.endLine();
        String link = projectUrl + "/-/blob/" + revision + "/" + encodedPath(diff.path()) + "#L" + lines;
        return "**[" + label + "](" + link + ")**\n\n" + postedBody(item);
    }

    private static String encodedPath(String path) {
        return Arrays.stream(path.split("/", -1))
                .map(segment ->
                        URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

    private static String fallbackBody(FeedbackAnchor diff, LineNote item) {
        return "**`" + diff.path() + ":" + diff.endLine() + "`**\n\n" + postedBody(item);
    }

    private record Copy(
            boolean verified,
            String noteId,
            @Nullable String discussionId,
            @Nullable String url,
            @Nullable CopyForm copyForm) {
        CopyReceipt preserved(LineNote item) {
            return new CopyReceipt(item.copyId(), CopyDisposition.PRESERVED_EXISTING, noteId, url, null, copyForm);
        }
    }

    private record Attempt(CopyReceipt receipt, boolean stop) {}

    private static boolean isRateLimitError(Exception e) {
        String message = e.getMessage();
        return message != null && (message.contains("rate limit") || message.contains("429"));
    }

    @Override
    public List<CopyUpdateReceipt> retract(FeedbackTarget target, List<CopyReceipt> copies) {
        targets.subject(target, kind());
        long workspaceId = target.item().workspaceId();
        List<CopyUpdateReceipt> outcomes = new ArrayList<>();
        for (CopyReceipt copy : copies) {
            String externalRef = copy.externalRef();
            if (externalRef == null || externalRef.isBlank()) {
                outcomes.add(
                        CopyUpdateReceipt.transientFailure(copy.copyId(), "Copy has no recorded provider reference"));
                continue;
            }
            try {
                ClientGraphQlResponse read = gitLabProvider
                        .forScope(workspaceId)
                        .document(
                                "query ReadPracticeCopy($id:ID!){currentUser{id} node(id:$id){__typename ... on Note{id body author{id}}}}")
                        .variable("id", externalRef)
                        .execute()
                        .block(GRAPHQL_TIMEOUT);
                if (read == null || !read.getErrors().isEmpty()) {
                    outcomes.add(CopyUpdateReceipt.transientFailure(copy.copyId(), "Existing copy could not be read"));
                    continue;
                }
                Object node = read.field("node").getValue();
                if (node == null) {
                    outcomes.add(CopyUpdateReceipt.gone(copy.copyId(), "The provider copy no longer exists"));
                    continue;
                }
                if (!(node instanceof Map<?, ?> note)
                        || !externalRef.equals(note.get("id"))
                        || !(note.get("body") instanceof String original)
                        || !(note.get("author") instanceof Map<?, ?> author
                                && Objects.equals(
                                        author.get("id"),
                                        read.field("currentUser.id").getValue()))) {
                    outcomes.add(new CopyUpdateReceipt(
                            copy.copyId(),
                            CopyUpdateKind.UNSUPPORTED,
                            externalRef,
                            copy.externalUrl(),
                            "Existing copy authority could not be verified"));
                    continue;
                }
                egressGuard.requireDeliveryAllowed("gitlab.correct-feedback-copy");
                ClientGraphQlResponse response = gitLabProvider
                        .forScope(workspaceId)
                        .document(
                                "mutation CorrectPracticeNote($id:NoteID!,$body:String!){updateNote(input:{id:$id,body:$body}){note{id url} errors}}")
                        .variable("id", externalRef)
                        .variable("body", ScmFeedbackFormatter.withdrawnCopyBody(original))
                        .execute()
                        .block(GRAPHQL_TIMEOUT);
                if (response == null
                        || !response.getErrors().isEmpty()
                        || (response.field("updateNote.errors").getValue() instanceof List<?> errors
                                && !errors.isEmpty())) {
                    outcomes.add(CopyUpdateReceipt.transientFailure(
                            copy.copyId(), "Provider did not confirm the correction"));
                    continue;
                }
                String id = response.field("updateNote.note.id").getValue();
                String url = response.field("updateNote.note.url").getValue();
                outcomes.add(
                        id == null || !id.equals(externalRef)
                                ? CopyUpdateReceipt.transientFailure(
                                        copy.copyId(), "Provider did not confirm the existing copy")
                                : new CopyUpdateReceipt(copy.copyId(), CopyUpdateKind.EDITED, id, url, null));
            } catch (OutboundEgressSuppressedException e) {
                throw e;
            } catch (RuntimeException e) {
                outcomes.add(CopyUpdateReceipt.transientFailure(copy.copyId(), "Provider correction failed"));
            }
        }
        return List.copyOf(outcomes);
    }
}
