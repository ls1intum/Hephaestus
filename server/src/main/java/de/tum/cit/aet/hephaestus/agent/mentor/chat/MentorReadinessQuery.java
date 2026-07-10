package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import org.springframework.modulith.NamedInterface;

@NamedInterface(name = "mentor-chat")
public interface MentorReadinessQuery {
    boolean isReady(long workspaceId);
}
