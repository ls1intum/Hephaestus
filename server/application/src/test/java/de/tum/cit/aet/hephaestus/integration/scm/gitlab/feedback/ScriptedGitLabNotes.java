package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressGuard;
import de.tum.cit.aet.hephaestus.integration.core.spi.SummaryWriter;
import de.tum.cit.aet.hephaestus.integration.scm.feedback.ScmFeedbackFixtures;
import de.tum.cit.aet.hephaestus.integration.scm.feedback.ScmFeedbackTargetResolver;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.testconfig.ScriptedCommentThreads;
import de.tum.cit.aet.hephaestus.testconfig.ScriptedGraphQlClient;
import de.tum.cit.aet.hephaestus.testconfig.ScriptedGraphQlClient.Request;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.graphql.client.ClientGraphQlResponse;
import reactor.core.publisher.Mono;

public final class ScriptedGitLabNotes {

    private static final String BOT = "gid://gitlab/User/1";

    private final ScriptedCommentThreads threads = new ScriptedCommentThreads("gid://gitlab/Note/");
    private final SummaryWriter channel;
    private boolean rejectPositions;
    private final Map<String, Object> positions = new ConcurrentHashMap<>();
    private final GitLabGraphQlClientProvider provider;

    public ScriptedGitLabNotes(OutboundEgressGuard egressGuard) {
        this(egressGuard, ScmFeedbackFixtures.resolver());
    }

    ScriptedGitLabNotes(OutboundEgressGuard egressGuard, ScmFeedbackTargetResolver targets) {
        provider = mock(GitLabGraphQlClientProvider.class);
        when(provider.forScope(anyLong())).thenReturn(ScriptedGraphQlClient.of(this::answer));
        channel = new GitLabSummaryWriter(provider, new GitLabMrResolver(provider), egressGuard, targets);
    }

    void rejectPositions(boolean reject) {
        rejectPositions = reject;
    }

    GitLabGraphQlClientProvider provider() {
        return provider;
    }

    public SummaryWriter channel() {
        return channel;
    }

    public ScriptedCommentThreads threads() {
        return threads;
    }

    public static String issue(int iid) {
        return "gid://gitlab/Issue/" + iid;
    }

    public static String mergeRequest(int iid) {
        return "gid://gitlab/MergeRequest/" + iid;
    }

    private Mono<ClientGraphQlResponse> answer(Request request) {
        return switch (request.document()) {
            case "GetIssueGlobalId" -> threads.resolve(Map.of("project.issue.id", issue(iid(request))));
            case "GetMergeRequestGlobalId" ->
                threads.resolve(Map.of("project.mergeRequest.id", mergeRequest(iid(request))));
            case "CreateDiffNote" -> {
                if (rejectPositions) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("note", null);
                    payload.put("errors", List.of("Position is invalid"));
                    yield ScriptedGraphQlClient.respond(Map.of("createDiffNote", payload));
                }
                yield threads.write(request.text("noteableId"), request.text("body"), id -> {
                    Object position = request.variables().get("position");
                    if (position == null) throw new IllegalArgumentException("Missing position");
                    positions.put(id, position);
                    return Map.of("createDiffNote", Map.of("note", Map.of("id", id), "errors", List.of()));
                });
            }
            case "GetMergeRequestDiscussions" ->
                threads.read(
                        mergeRequest(iid(request)),
                        notes -> Map.of(
                                "currentUser.id",
                                BOT,
                                "project.mergeRequest.discussions",
                                Map.of(
                                        "pageInfo",
                                        Map.of("hasNextPage", false),
                                        "nodes",
                                        notes.stream()
                                                .map(note -> {
                                                    Map<String, Object> node = new HashMap<>();
                                                    node.put("id", note.id());
                                                    node.put("body", note.body());
                                                    node.put("author", Map.of("id", BOT));
                                                    Object raw = positions.get(note.id());
                                                    if (raw instanceof Map<?, ?> position
                                                            && position.get("paths") instanceof Map<?, ?> paths) {
                                                        Map<String, Object> projected = new HashMap<>();
                                                        projected.put("oldPath", paths.get("oldPath"));
                                                        projected.put("newPath", paths.get("newPath"));
                                                        projected.put("oldLine", position.get("oldLine"));
                                                        projected.put("newLine", position.get("newLine"));
                                                        projected.put(
                                                                "diffRefs",
                                                                Map.of(
                                                                        "baseSha",
                                                                        position.get("baseSha"),
                                                                        "headSha",
                                                                        position.get("headSha"),
                                                                        "startSha",
                                                                        position.get("startSha")));
                                                        node.put("position", projected);
                                                    } else node.put("position", null);
                                                    return Map.of(
                                                            "id",
                                                            "discussion-" + note.id(),
                                                            "notes",
                                                            Map.of(
                                                                    "nodes",
                                                                    List.of(node),
                                                                    "pageInfo",
                                                                    Map.of("hasNextPage", false)));
                                                })
                                                .toList())));
            case "CreateMergeRequestNote" ->
                threads.write(
                        request.text("noteableId"),
                        request.text("body"),
                        id -> Map.of(
                                "createNote.note.id",
                                id,
                                "createNote.errors",
                                List.of(),
                                "createNote",
                                Map.of("note", Map.of("id", id), "errors", List.of())));
            case "GetIssueNotesNewest" -> notes("project.issue.notes", issue(iid(request)));
            case "GetMergeRequestNotesNewest" -> notes("project.mergeRequest.notes", mergeRequest(iid(request)));
            default -> Mono.error(new IllegalStateException("Unscripted GitLab document " + request.document()));
        };
    }

    private Mono<ClientGraphQlResponse> notes(String path, String noteable) {
        return threads.read(
                noteable,
                notes -> Map.of(
                        "currentUser.id",
                        BOT,
                        path,
                        Map.of(
                                "nodes",
                                notes.stream()
                                        .map(note -> Map.of(
                                                "id", note.id(), "body", note.body(), "author", Map.of("id", BOT)))
                                        .toList(),
                                "pageInfo",
                                Map.of("hasPreviousPage", false))));
    }

    private static int iid(Request request) {
        return Integer.parseInt(request.text("iid"));
    }
}
