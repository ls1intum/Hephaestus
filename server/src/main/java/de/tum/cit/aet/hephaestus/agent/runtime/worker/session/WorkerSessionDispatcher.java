package de.tum.cit.aet.hephaestus.agent.runtime.worker.session;

import de.tum.cit.aet.hephaestus.agent.runtime.worker.session.mentor.MentorSessionRunner;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionClose;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionCloseReason;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionInput;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionKind;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.SessionOpen;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.WorkerControlFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Routes {@code Session*} frames to the per-{@link SessionKind} runner; owns the drain fan-out. */
public class WorkerSessionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WorkerSessionDispatcher.class);

    private final MentorSessionRunner mentorRunner;

    public WorkerSessionDispatcher(MentorSessionRunner mentorRunner) {
        this.mentorRunner = mentorRunner;
    }

    public void accept(WorkerControlFrame frame) {
        switch (frame) {
            case SessionOpen open -> handleOpen(open);
            case SessionInput input -> mentorRunner.onInput(input);
            case SessionClose close -> mentorRunner.onClose(close);
            default -> log.debug("Dropping non-session frame in dispatcher: {}", frame.getClass().getSimpleName());
        }
    }

    private void handleOpen(SessionOpen open) {
        if (open.kind() == SessionKind.MENTOR_INTERACTIVE) {
            mentorRunner.onOpen(open);
        } else {
            log.warn("Unsupported SessionKind={} on this worker; ignoring", open.kind());
        }
    }

    public void closeAll(SessionCloseReason reason) {
        mentorRunner.closeAll(reason);
    }
}
