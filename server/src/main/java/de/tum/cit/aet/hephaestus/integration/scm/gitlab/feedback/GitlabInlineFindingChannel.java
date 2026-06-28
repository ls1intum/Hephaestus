package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import static de.tum.cit.aet.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitlabMrResolver.GRAPHQL_TIMEOUT;

import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.FindingAnchor;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel.DeliveredSignal;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel.Disposition;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitlabMrResolver.MrCoordinates;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitlabMrResolver.MrInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.stereotype.Component;

/**
 * GitLab adapter for {@link InlineFindingChannel}. Posts inline diff notes one at a
 * time via {@code CreateDiffNote} (GitLab has no batch API). For positions outside the
 * diff hunk, falls back to a regular MR comment with {@code file:line} prefix.
 *
 * <p>Reconciles by {@code recurrenceKey} rather than clear-then-post: each finding's stable key is embedded
 * in the note body as a hidden HTML tag, and before posting we read the MR's existing discussions
 * ({@code GetMergeRequestDiscussions}) and index this reviewer's own prior threads by that key. A finding whose
 * key matches a prior, non-human-replied thread is EDITED in place ({@code UpdateNote}) so a stable finding
 * keeps its single thread across re-runs instead of being deleted and re-created; a human-replied thread is
 * PRESERVED untouched; an unmatched finding is posted as a fresh {@code CreateDiffNote} thread. Prior bot
 * threads whose key is absent from the current run AND have no human reply are the truly-gone ones — those, and
 * only those, are {@code DestroyNote}d. Reconciliation reads are best-effort; a failed read degrades to
 * fresh posts (still keyed) rather than blocking delivery.
 *
 * <p>The {@link #clearStaleFindings} path remains for the zero-note re-run (the empty-diff pathology) where
 * there are no findings to reconcile against and every prior note is therefore stale.
 *
 * <p>Non-{@link FindingAnchor.DiffAnchor} anchors are counted as failed.
 *
 * <p>Gated on {@code hephaestus.integration.gitlab.enabled=true} to track
 * {@link GitLabGraphQlClientProvider}.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.gitlab.enabled", havingValue = "true", matchIfMissing = false)
public class GitlabInlineFindingChannel implements InlineFindingChannel {

    private static final Logger log = LoggerFactory.getLogger(GitlabInlineFindingChannel.class);

    /** GitLab's max per-page limit for note pagination — sufficient since we post at most ~30 notes per review. */
    private static final int NOTES_PAGE_SIZE = 500;

    /**
     * Hidden per-finding correlation tag embedded in a note body so a prior thread can be matched back to the
     * finding that produced it across re-runs. Distinct from the run-level {@code marker} (which identifies all
     * hephaestus notes for the zero-note clear path); both coexist in the body. The key is alnum/dash/underscore
     * (a {@link de.tum.cit.aet.hephaestus.practices.observation.ObservationFingerprint} digest), so no escaping is needed.
     */
    private static final Pattern CK_TAG = Pattern.compile("<!-- hephaestus-diff-note-ck=([A-Za-z0-9_-]+) -->");

    private final GitLabGraphQlClientProvider gitLabProvider;
    private final GitlabMrResolver mrResolver;

    public GitlabInlineFindingChannel(GitLabGraphQlClientProvider gitLabProvider, GitlabMrResolver mrResolver) {
        this.gitLabProvider = gitLabProvider;
        this.mrResolver = mrResolver;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    /**
     * Deletes every marker-bearing inline note on the MR without posting new ones — the clear half of
     * clear-then-post (SPI {@link InlineFindingChannel#clearStaleFindings}). Called on a zero-note re-run so
     * a PR re-reviewed into nothing-inline doesn't keep line-numbered notes on code no longer in the diff.
     */
    @Override
    public void clearStaleFindings(FeedbackChannel.FeedbackTarget target, String marker) {
        if (marker == null || marker.isBlank()) {
            return;
        }
        long scopeId = target.ref().workspaceId();
        if (gitLabProvider.isRateLimitCritical(scopeId)) {
            log.warn("GitLab rate limit critical — skipping stale inline-note clear: workspaceId={}", scopeId);
            return;
        }
        MrCoordinates mr = GitlabMrResolver.parseSubjectExternalId(target.subjectExternalId());
        deleteOldMarkedNotes(scopeId, mr.projectPath(), mr.iid(), marker);
    }

    @Override
    public InlineResult postInlineFindings(FeedbackChannel.FeedbackTarget target, List<InlineFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return InlineResult.counts(0, 0);
        }
        long scopeId = target.ref().workspaceId();
        if (gitLabProvider.isRateLimitCritical(scopeId)) {
            log.warn(
                "GitLab rate limit critical — skipping {} inline findings: workspaceId={}",
                findings.size(),
                scopeId
            );
            return InlineResult.counts(0, findings.size());
        }

        MrCoordinates mr = GitlabMrResolver.parseSubjectExternalId(target.subjectExternalId());
        MrInfo mrInfo = mrResolver.resolve(scopeId, mr.projectPath(), mr.iid());
        if (mrInfo.headSha() == null || mrInfo.startSha() == null) {
            log.warn(
                "GitLab MR missing diffRefs — skipping diff notes: workspaceId={}, mrGid={}",
                scopeId,
                mrInfo.globalId()
            );
            return InlineResult.counts(0, findings.size());
        }

        // Index this reviewer's prior threads by correlation key so a stable finding edits its existing thread
        // instead of being cleared-then-reposted. Best-effort: a failed read yields an empty index, degrading to
        // fresh keyed posts (no edit, no delete) rather than blocking delivery.
        String marker = findings.get(0).marker();
        Map<String, PriorThread> priorByKey = indexPriorThreads(scopeId, mr.projectPath(), mr.iid(), marker);

        int posted = 0;
        int failed = 0;
        int remaining = findings.size();
        boolean rateLimited = false;
        Set<String> seenKeys = new HashSet<>();
        List<DeliveredSignal> signals = new ArrayList<>(findings.size());

        for (InlineFinding finding : findings) {
            remaining--;
            if (!(finding.anchor() instanceof FindingAnchor.DiffAnchor diff)) {
                log.warn("Skipping non-diff anchor on GitLab inline finding: anchor={}", finding.anchor());
                failed++;
                signals.add(failedSignal(finding));
                continue;
            }
            // Register the key as seen BEFORE the blank-body guard: a finding whose key is still present this
            // run must never be reaped by destroyVanishedThreads, regardless of body content. Otherwise a
            // valid-key, blank-body finding would silently delete its own still-current prior thread.
            String key = finding.recurrenceKey();
            if (key != null) {
                seenKeys.add(key);
            }
            if (finding.body() == null || finding.body().isBlank()) {
                continue;
            }

            PriorThread prior = key == null ? null : priorByKey.get(key);

            // A prior thread a developer engaged with is left exactly as is — neither edited nor deleted.
            if (prior != null && prior.humanReplied()) {
                posted++; // the finding IS represented on the MR, just not by us this run
                signals.add(
                    new DeliveredSignal(key, diff, Disposition.PRESERVED_EXISTING, prior.noteId(), prior.discussionId())
                );
                continue;
            }

            String body = appendCorrelationTag(
                appendMarker(GitlabFeedbackChannel.escapeSlashCommands(finding.body()), marker),
                key
            );

            try {
                Outcome outcome =
                    prior != null ? editInPlace(scopeId, prior, body, diff) : createThread(scopeId, mrInfo, diff, body);
                if (outcome.disposition() == Disposition.FELL_BACK || outcome.disposition() == Disposition.POSTED) {
                    posted++;
                } else {
                    failed++;
                }
                signals.add(
                    new DeliveredSignal(key, diff, outcome.disposition(), outcome.noteId(), outcome.discussionId())
                );
            } catch (RateLimitHit e) {
                log.warn("GitLab rate limit hit during diff note posting — stopping: workspaceId={}", scopeId);
                failed += remaining + 1;
                signals.add(failedSignal(finding));
                rateLimited = true;
                break;
            }
        }

        // Delete only the prior bot threads that this run did NOT re-emit and that no developer touched — the
        // findings that genuinely went away. Edited/preserved threads are excluded by key; human-replied ones
        // are never destroyed.
        //
        // NEVER reap on a mid-batch rate limit: a 429 abandons the loop before the un-processed findings could
        // register their keys in seenKeys, so destroyVanishedThreads would see still-current findings as
        // "vanished" and delete their live threads. Skip the destroy entirely this run; the next reconcile
        // (with a full seenKeys) reaps anything genuinely gone.
        int deletedGone = rateLimited ? 0 : destroyVanishedThreads(scopeId, priorByKey, seenKeys);

        log.info(
            "Reconciled GitLab inline findings: posted/edited={}, failed={}, deleted-gone={}, workspaceId={}",
            posted,
            failed,
            deletedGone,
            scopeId
        );
        return new InlineResult(posted, failed, List.copyOf(signals));
    }

    /** Posts a brand-new diff-note thread; falls back to an MR comment when the line is outside the diff hunk. */
    private Outcome createThread(long scopeId, MrInfo mrInfo, FindingAnchor.DiffAnchor diff, String body) {
        try {
            Map<String, Object> position = buildPosition(diff, mrInfo);
            ClientGraphQlResponse response = gitLabProvider
                .forScope(scopeId)
                .documentName("CreateDiffNote")
                .variable("noteableId", mrInfo.globalId())
                .variable("body", body)
                .variable("position", position)
                .execute()
                .block(GRAPHQL_TIMEOUT);

            if (response == null) {
                log.warn("Null response posting GitLab diff note: workspaceId={}, file={}", scopeId, diff.filePath());
                return Outcome.failed();
            }

            List<String> errors = response.field("createDiffNote.errors").getValue();
            if (errors != null && !errors.isEmpty()) {
                if (isLineCodeError(errors)) {
                    log.info(
                        "Diff note line outside diff hunk, falling back to MR comment: workspaceId={}, file={}, line={}",
                        scopeId,
                        diff.filePath(),
                        diff.newLineNumber()
                    );
                    String noteId = postFallbackComment(scopeId, mrInfo.globalId(), diff, body);
                    return noteId != null ? new Outcome(Disposition.FELL_BACK, noteId, null) : Outcome.failed();
                }
                log.warn(
                    "GitLab createDiffNote failed: workspaceId={}, file={}, line={}, errors={}",
                    scopeId,
                    sanitizeForLog(diff.filePath()),
                    diff.newLineNumber(),
                    sanitizeForLog(errors.toString())
                );
                return Outcome.failed();
            }

            return new Outcome(Disposition.POSTED, noteIdOf(response), discussionIdOf(response));
        } catch (Exception e) {
            if (isRateLimitError(e)) {
                throw new RateLimitHit();
            }
            log.warn(
                "GitLab diff note failed: workspaceId={}, file={}, line={}",
                scopeId,
                sanitizeForLog(diff.filePath()),
                diff.newLineNumber(),
                e
            );
            return Outcome.failed();
        }
    }

    /** Edits a matched prior bot note's body in place ({@code UpdateNote}), keeping its thread and anchor. */
    private Outcome editInPlace(long scopeId, PriorThread prior, String body, FindingAnchor.DiffAnchor diff) {
        try {
            ClientGraphQlResponse response = gitLabProvider
                .forScope(scopeId)
                .documentName("UpdateNote")
                .variable("id", prior.noteId())
                .variable("body", body)
                .execute()
                .block(GRAPHQL_TIMEOUT);

            if (response == null) {
                log.warn("Null response editing GitLab diff note: workspaceId={}, noteId={}", scopeId, prior.noteId());
                return Outcome.failed();
            }

            List<String> errors = response.field("updateNote.errors").getValue();
            if (errors != null && !errors.isEmpty()) {
                log.warn(
                    "GitLab updateNote failed: workspaceId={}, noteId={}, errors={}",
                    scopeId,
                    prior.noteId(),
                    sanitizeForLog(errors.toString())
                );
                return Outcome.failed();
            }
            return new Outcome(Disposition.POSTED, prior.noteId(), prior.discussionId());
        } catch (Exception e) {
            if (isRateLimitError(e)) {
                throw new RateLimitHit();
            }
            log.warn(
                "GitLab diff note edit failed: workspaceId={}, file={}, line={}",
                scopeId,
                sanitizeForLog(diff.filePath()),
                diff.newLineNumber(),
                e
            );
            return Outcome.failed();
        }
    }

    /**
     * Reads the MR's discussions and indexes this reviewer's own prior threads by their embedded correlation
     * key. A discussion is "ours" when it has a marker-bearing note carrying a key; if it also has any
     * non-system note WITHOUT the marker, a human (or another tool) joined it and the thread is flagged as
     * human-replied. Best-effort: any failure yields an empty index so delivery degrades to fresh keyed posts.
     */
    private Map<String, PriorThread> indexPriorThreads(long scopeId, String projectPath, int mrIid, String marker) {
        Map<String, PriorThread> byKey = new LinkedHashMap<>();
        if (marker == null || marker.isBlank()) {
            return byKey;
        }
        try {
            ClientGraphQlResponse response = gitLabProvider
                .forScope(scopeId)
                .documentName("GetMergeRequestDiscussions")
                .variable("fullPath", projectPath)
                .variable("iid", String.valueOf(mrIid))
                .variable("first", NOTES_PAGE_SIZE)
                .execute()
                .block(GRAPHQL_TIMEOUT);

            if (response == null) {
                return byKey;
            }
            List<Map<String, Object>> discussions = response.field("project.mergeRequest.discussions.nodes").getValue();
            if (discussions == null || discussions.isEmpty()) {
                return byKey;
            }

            for (Map<String, Object> discussion : discussions) {
                indexDiscussion(discussion, marker, byKey);
            }
        } catch (Exception e) {
            log.debug("Failed to read MR discussions for correlation reconcile: workspaceId={}", scopeId, e);
        }
        return byKey;
    }

    /** Indexes one discussion's marked bot note (if any) under its parsed correlation key. */
    private static void indexDiscussion(Map<String, Object> discussion, String marker, Map<String, PriorThread> byKey) {
        List<Map<String, Object>> notes = notesOf(discussion);
        if (notes.isEmpty()) {
            return;
        }
        String discussionId = (String) discussion.get("id");
        String botNoteId = null;
        String botKey = null;
        boolean humanReplied = false;
        for (Map<String, Object> note : notes) {
            if (Boolean.TRUE.equals(note.get("system"))) {
                continue; // GitLab system notes ("changed the description", etc.) never count.
            }
            String body = (String) note.get("body");
            String noteId = (String) note.get("id");
            if (noteId == null || body == null) {
                continue;
            }
            if (body.contains(marker)) {
                botNoteId = noteId;
                botKey = parseObservationFingerprint(body);
            } else {
                humanReplied = true; // a person (or other tool) participated in this thread
            }
        }
        if (botKey == null || botNoteId == null) {
            return; // not one of ours, or a legacy bot note posted before keys existed — leave the clear path to it
        }
        byKey.put(botKey, new PriorThread(botKey, botNoteId, discussionId, humanReplied));
    }

    /**
     * Destroys prior bot threads whose key is absent from the current run and that no developer engaged with —
     * the findings that genuinely went away. Returns the number deleted. Best-effort per note.
     */
    private int destroyVanishedThreads(long scopeId, Map<String, PriorThread> priorByKey, Set<String> seenKeys) {
        int deleted = 0;
        for (PriorThread prior : priorByKey.values()) {
            if (seenKeys.contains(prior.key()) || prior.humanReplied()) {
                continue;
            }
            if (destroyNote(scopeId, prior.noteId())) {
                deleted++;
            }
        }
        return deleted;
    }

    private static DeliveredSignal failedSignal(InlineFinding finding) {
        return new DeliveredSignal(finding.recurrenceKey(), finding.anchor(), Disposition.FAILED, null, null);
    }

    @Nullable
    private static String noteIdOf(ClientGraphQlResponse response) {
        return response.field("createDiffNote.note.id").getValue();
    }

    @Nullable
    private static String discussionIdOf(ClientGraphQlResponse response) {
        return response.field("createDiffNote.note.discussion.id").getValue();
    }

    @Nullable
    private static String parseObservationFingerprint(String body) {
        Matcher m = CK_TAG.matcher(body);
        return m.find() ? m.group(1) : null;
    }

    /** Appends the hidden per-finding correlation tag; a null key (pre-correlation finding) appends nothing. */
    private static String appendCorrelationTag(String body, @Nullable String recurrenceKey) {
        if (recurrenceKey == null || recurrenceKey.isBlank()) {
            return body;
        }
        return body + "\n<!-- hephaestus-diff-note-ck=" + recurrenceKey + " -->";
    }

    /** A prior diff-note thread we posted, matched by its embedded correlation key. */
    private record PriorThread(String key, String noteId, @Nullable String discussionId, boolean humanReplied) {}

    /** Result of a single create/edit attempt: what happened plus the durable note/discussion handles. */
    private record Outcome(Disposition disposition, @Nullable String noteId, @Nullable String discussionId) {
        static Outcome failed() {
            return new Outcome(Disposition.FAILED, null, null);
        }
    }

    /** Signals the per-finding loop to stop and fail the rest of the batch, mirroring the prior break-on-429. */
    private static final class RateLimitHit extends RuntimeException {}

    private static Map<String, Object> buildPosition(FindingAnchor.DiffAnchor diff, MrInfo mrInfo) {
        Map<String, Object> position = new HashMap<>();
        position.put("headSha", mrInfo.headSha());
        position.put("startSha", mrInfo.startSha());
        position.put("baseSha", mrInfo.baseSha());
        // oldPath required by GitLab to match the note position to the diff file in the
        // Changes tab. Correct for new + modified files. For renamed files oldPath
        // should be the pre-rename path, but the DiffAnchor only carries the new path.
        // Renames are rare in student assignments; if needed, resolve from MR diff metadata.
        Map<String, String> paths = new HashMap<>();
        paths.put("newPath", diff.filePath());
        paths.put("oldPath", diff.filePath());
        position.put("paths", paths);
        position.put("newLine", diff.newLineNumber());
        return position;
    }

    /**
     * Removes this reviewer's own stale inline notes before re-posting — but NEVER a thread a developer has
     * replied to (ADR 0021 re-review UX). We query discussions (not flat notes) so a marker-bearing note can
     * be judged in the context of its thread: a discussion that contains any non-system note WITHOUT our
     * marker means a human (or another tool) joined it, and deleting it would destroy their words. Such
     * threads are PRESERVED and left to the platform's own code-change-driven outdating — we deliberately do
     * not auto-resolve on non-detection, which is unsafe under the detector's run-to-run non-determinism.
     */
    private void deleteOldMarkedNotes(long scopeId, String projectPath, int mrIid, String marker) {
        if (marker == null || marker.isBlank()) {
            return;
        }
        try {
            ClientGraphQlResponse response = gitLabProvider
                .forScope(scopeId)
                .documentName("GetMergeRequestDiscussions")
                .variable("fullPath", projectPath)
                .variable("iid", String.valueOf(mrIid))
                .variable("first", NOTES_PAGE_SIZE)
                .execute()
                .block(GRAPHQL_TIMEOUT);

            if (response == null) {
                return;
            }

            List<Map<String, Object>> discussions = response.field("project.mergeRequest.discussions.nodes").getValue();
            if (discussions == null || discussions.isEmpty()) {
                return;
            }

            int deleted = 0;
            int preserved = 0;
            for (Map<String, Object> discussion : discussions) {
                List<Map<String, Object>> notes = notesOf(discussion);
                if (notes.isEmpty()) {
                    continue;
                }

                List<String> markedNoteIds = new ArrayList<>();
                boolean humanReplied = false;
                for (Map<String, Object> note : notes) {
                    if (Boolean.TRUE.equals(note.get("system"))) {
                        continue; // GitLab system notes ("changed the description", etc.) never count.
                    }
                    String body = (String) note.get("body");
                    String noteId = (String) note.get("id");
                    if (noteId == null || body == null) {
                        continue;
                    }
                    if (body.contains(marker)) {
                        markedNoteIds.add(noteId);
                    } else {
                        humanReplied = true; // a person (or other tool) participated in this thread
                    }
                }

                if (markedNoteIds.isEmpty()) {
                    continue;
                }
                if (humanReplied) {
                    preserved += markedNoteIds.size();
                    continue; // never destroy a thread a developer engaged with
                }
                for (String noteId : markedNoteIds) {
                    if (destroyNote(scopeId, noteId)) {
                        deleted++;
                    }
                }
            }

            if (deleted > 0 || preserved > 0) {
                log.info(
                    "Reconciled stale inline notes: deleted={}, preserved(human-replied)={}, workspaceId={}, mr={}!{}",
                    deleted,
                    preserved,
                    scopeId,
                    projectPath,
                    mrIid
                );
            }
        } catch (Exception e) {
            log.debug("Failed to reconcile existing MR discussions for dedup: workspaceId={}", scopeId, e);
        }
    }

    /** Safely pulls a discussion's {@code notes.nodes} list, tolerating nulls in the GraphQL map. */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> notesOf(Map<String, Object> discussion) {
        Object notesField = discussion.get("notes");
        if (!(notesField instanceof Map<?, ?> notesMap)) {
            return List.of();
        }
        Object nodes = notesMap.get("nodes");
        return nodes instanceof List ? (List<Map<String, Object>>) nodes : List.of();
    }

    /** Destroys a single note; returns true on success. Best-effort — failures are logged, never thrown. */
    private boolean destroyNote(long scopeId, String noteId) {
        try {
            ClientGraphQlResponse deleteResponse = gitLabProvider
                .forScope(scopeId)
                .documentName("DestroyNote")
                .variable("noteId", noteId)
                .execute()
                .block(GRAPHQL_TIMEOUT);
            if (deleteResponse == null) {
                return false;
            }
            List<String> errors = deleteResponse.field("destroyNote.errors").getValue();
            if (errors == null || errors.isEmpty()) {
                return true;
            }
            log.debug("Failed to delete old diff note: noteId={}, errors={}", noteId, errors);
            return false;
        } catch (Exception e) {
            log.debug("Failed to delete old diff note: noteId={}", noteId, e);
            return false;
        }
    }

    /**
     * Posts an out-of-hunk finding as a plain MR comment, prefixed with its {@code file:line} so the location is
     * still legible. {@code markedBody} already carries the marker + correlation tag (so a fallback comment is
     * reconciled like any other note); returns the new note id or {@code null} on failure.
     */
    @Nullable
    private String postFallbackComment(
        long scopeId,
        String mrGlobalId,
        FindingAnchor.DiffAnchor diff,
        String markedBody
    ) {
        try {
            String fallbackBody = String.format("**`%s:%d`**%n%n%s", diff.filePath(), diff.newLineNumber(), markedBody);
            ClientGraphQlResponse response = gitLabProvider
                .forScope(scopeId)
                .documentName("CreateMergeRequestNote")
                .variable("noteableId", mrGlobalId)
                .variable("body", fallbackBody)
                .execute()
                .block(GRAPHQL_TIMEOUT);

            if (response == null) {
                log.warn("Null response posting fallback MR comment: workspaceId={}", scopeId);
                return null;
            }

            List<String> errors = response.field("createNote.errors").getValue();
            if (errors != null && !errors.isEmpty()) {
                log.warn(
                    "Fallback MR comment failed: workspaceId={}, errors={}",
                    scopeId,
                    sanitizeForLog(errors.toString())
                );
                return null;
            }
            return response.field("createNote.note.id").getValue();
        } catch (Exception e) {
            log.warn(
                "Fallback MR comment failed: workspaceId={}, file={}",
                scopeId,
                sanitizeForLog(diff.filePath()),
                e
            );
            return null;
        }
    }

    /** Returns the marker shared by all findings in the batch (they originate from one parser pass). */
    private static String appendMarker(String body, String marker) {
        if (marker == null || marker.isBlank()) {
            return body;
        }
        return body + "\n" + marker;
    }

    private static boolean isRateLimitError(Exception e) {
        String message = e.getMessage();
        return message != null && (message.contains("rate limit") || message.contains("429"));
    }

    private static boolean isLineCodeError(List<String> errors) {
        return errors
            .stream()
            .anyMatch(e -> e.toLowerCase().contains("line code") || e.toLowerCase().contains("line_code"));
    }
}
