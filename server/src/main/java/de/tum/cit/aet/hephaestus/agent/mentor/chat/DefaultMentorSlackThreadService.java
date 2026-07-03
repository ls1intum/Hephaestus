package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.mentor.ChatThread;
import de.tum.cit.aet.hephaestus.mentor.ChatThreadRepository;
import de.tum.cit.aet.hephaestus.mentor.ThreadSurface;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link MentorSlackThreadService}: creates the {@code SLACK_DM} {@code chat_thread} inside the mentor
 * module so thread creation never happens through a cross-module raw insert. The subsequent turn's
 * {@code MentorTurnPersistence#ensureThread} finds this pre-created, correctly-owned thread.
 */
@Service
@RequiredArgsConstructor
class DefaultMentorSlackThreadService implements MentorSlackThreadService {

    private final ChatThreadRepository chatThreadRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;

    @Override
    @Transactional
    public UUID ensureSlackThread(long workspaceId, @Nullable UUID chatThreadId, String developerLogin) {
        if (chatThreadId != null) {
            var existing = chatThreadRepository.findByIdAndWorkspaceId(chatThreadId, workspaceId);
            if (existing.isPresent()) {
                return existing.get().getId();
            }
        }
        User user = userRepository
            .findByLogin(developerLogin)
            .orElseThrow(() -> new EntityNotFoundException("User", developerLogin));
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", String.valueOf(workspaceId)));
        ChatThread thread = new ChatThread();
        thread.setId(chatThreadId != null ? chatThreadId : UUID.randomUUID());
        thread.setUser(user);
        thread.setWorkspace(workspace);
        thread.setSurface(ThreadSurface.SLACK_DM);
        return chatThreadRepository.save(thread).getId();
    }

    @Override
    @Transactional
    public int purgeSlackThreads(long workspaceId) {
        // Bulk DELETE of the SLACK_DM chat_thread rows; the DB ON DELETE CASCADE FKs drop the linked chat_message
        // rows and the integration.slack mentor_slack_thread mapping. The WEB mentor history is left intact.
        return chatThreadRepository.deleteByWorkspaceIdAndSurface(workspaceId, ThreadSurface.SLACK_DM);
    }
}
