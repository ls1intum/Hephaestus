package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import org.springframework.modulith.NamedInterface;

/**
 * The one seam other modules use to run a mentor turn on a non-HTTP surface (e.g. a Slack DM). Runs the turn
 * for the developer identified by {@code developerLogin}, streaming it to the caller-supplied
 * {@link MentorChannel}. Exposed as the {@code mentor-chat} Modulith named interface (with {@code propagate}
 * so {@link MentorChannel} + {@link MentorTurnRequest} + the wire chunk types come along) so
 * {@code integration.slack} can depend on this narrow port instead of the whole {@code agent.mentor.chat}
 * package.
 */
@NamedInterface(name = "mentor-chat", propagate = true)
public interface MentorTurnRunner {
    /**
     * @param developerLogin the SCM login the turn resolves the developer {@code User} from (set on the turn
     *     thread's identity holder); the caller is responsible for having authenticated/resolved it.
     */
    void run(MentorTurnRequest request, MentorChannel channel, String developerLogin);
}
