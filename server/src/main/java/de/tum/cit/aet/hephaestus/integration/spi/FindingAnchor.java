package de.tum.cit.aet.hephaestus.integration.spi;

import org.springframework.lang.Nullable;

/**
 * Anchor for an inline finding. Sealed with per-family permits — SCM uses
 * diff coordinates, knowledge bases use document anchors, messaging uses thread
 * coordinates, project trackers degrade to issue-comment.
 *
 * <p>Compile-time exhaustive — adding a new anchor type forces every consumer to
 * handle it.
 */
public sealed interface FindingAnchor
    permits FindingAnchor.DiffAnchor,
            FindingAnchor.DocumentAnchor,
            FindingAnchor.ChannelThreadAnchor,
            FindingAnchor.IssueAnchor {

    /** SCM diff coordinates. {@code side} disambiguates multi-line inline shapes (Bitbucket). */
    record DiffAnchor(
        String filePath,
        int newLineNumber,
        @Nullable Integer startLine,
        DiffSide side
    ) implements FindingAnchor {
        public DiffAnchor(String filePath, int newLineNumber, @Nullable Integer startLine) {
            this(filePath, newLineNumber, startLine, DiffSide.RIGHT);
        }
    }

    enum DiffSide { LEFT, RIGHT, BOTH }

    /**
     * Knowledge-base document anchor. Sealed across the document-anchor shapes used
     * by Outline (ProseMirror node), Confluence (ADF), Notion (block UUID),
     * Markdown-rendered headings, and whole-page comments.
     */
    sealed interface DocumentAnchor extends FindingAnchor
        permits DocumentAnchor.BlockAnchor,
                DocumentAnchor.ProseMirrorAnchor,
                DocumentAnchor.AdfNodeAnchor,
                DocumentAnchor.HeadingAnchor,
                DocumentAnchor.PageAnchor {

        String documentId();

        record BlockAnchor(String documentId, String blockId) implements DocumentAnchor {}
        record ProseMirrorAnchor(String documentId, String path) implements DocumentAnchor {}
        record AdfNodeAnchor(String documentId, String nodeLocalId) implements DocumentAnchor {}
        record HeadingAnchor(String documentId, String headingText) implements DocumentAnchor {}
        record PageAnchor(String documentId) implements DocumentAnchor {}
    }

    /** Messaging thread coordinates — Slack channel + parent ts, similar for Discord/Teams. */
    record ChannelThreadAnchor(String channelId, @Nullable String parentTs) implements FindingAnchor {
    }

    /**
     * Project-tracker issue anchor. Tracker descriptions have no anchor IDs across Linear/Jira/Asana,
     * so we degrade to per-issue (with optional in-thread comment id).
     */
    record IssueAnchor(String issueExternalId, @Nullable String commentExternalId) implements FindingAnchor {
    }
}
