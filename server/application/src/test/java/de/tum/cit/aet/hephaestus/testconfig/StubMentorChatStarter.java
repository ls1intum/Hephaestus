package de.tum.cit.aet.hephaestus.testconfig;

import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorChatStarter;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorTurnRequest;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Primary;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Primary
public class StubMentorChatStarter implements MentorChatStarter {

    private volatile CountDownLatch invocation = new CountDownLatch(1);

    public void reset() {
        invocation = new CountDownLatch(1);
    }

    public boolean awaitInvocation() throws InterruptedException {
        return invocation.await(2, TimeUnit.SECONDS);
    }

    @Override
    public void start(MentorTurnRequest request, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().data("[DONE]"));
            emitter.complete();
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        } finally {
            invocation.countDown();
        }
    }
}
