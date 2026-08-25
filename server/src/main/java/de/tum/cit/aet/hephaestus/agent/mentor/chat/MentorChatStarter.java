package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface MentorChatStarter {
    void start(MentorTurnRequest request, SseEmitter emitter);
}
