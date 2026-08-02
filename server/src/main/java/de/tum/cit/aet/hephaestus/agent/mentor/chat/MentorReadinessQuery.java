package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import org.springframework.modulith.NamedInterface;

/** Evaluates workspace mentor admission and operational readiness. Implementations fail closed. */
@NamedInterface(name = "mentor-chat")
public interface MentorReadinessQuery {
    boolean isEnabled(long workspaceId);

    boolean isReady(long workspaceId);
}
