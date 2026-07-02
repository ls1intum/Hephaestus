package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.modulith.NamedInterface;

/**
 * The mentor-owned seam for provisioning a {@code chat_thread} that a non-web surface (a Slack DM) will drive.
 * Exposed as part of the {@code mentor-chat} Modulith named interface so {@code integration.slack} can ask the
 * mentor module to create the thread — rather than reaching across the module boundary with a raw
 * {@code INSERT INTO chat_thread}. The Slack side then persists only its own {@code mentor_slack_thread} mapping
 * row (which carries the returned id as a scalar cross-module reference).
 */
@NamedInterface(name = "mentor-chat")
public interface MentorSlackThreadService {
    /**
     * Return the id of the {@code chat_thread} (surface {@code SLACK_DM}) that backs a Slack DM, creating it if
     * necessary. When {@code chatThreadId} is non-null and already exists for {@code workspaceId}, it is returned
     * unchanged; otherwise a fresh thread is created, owned by the developer resolved from {@code developerLogin}.
     *
     * @param workspaceId    the workspace the thread belongs to
     * @param chatThreadId   an existing thread id to reuse, or {@code null} to mint a new one
     * @param developerLogin the SCM login whose {@code User} owns the thread
     * @return the id of the backing {@code chat_thread}
     */
    UUID ensureSlackThread(long workspaceId, @Nullable UUID chatThreadId, String developerLogin);
}
