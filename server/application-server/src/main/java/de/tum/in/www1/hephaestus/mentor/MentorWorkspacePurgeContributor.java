package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.workspace.spi.WorkspacePurgeContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bulk-deletes mentor chat threads (cascades to messages, parts, votes, session_jsonl)
 * when a workspace is purged. Without this, soft-purged workspaces leak chat history
 * indefinitely — the FK cascade only fires on hard workspace deletes.
 */
@Component
@RequiredArgsConstructor
public class MentorWorkspacePurgeContributor implements WorkspacePurgeContributor {

    private final ChatThreadRepository chatThreadRepository;

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        chatThreadRepository.deleteByWorkspaceId(workspaceId);
    }
}
