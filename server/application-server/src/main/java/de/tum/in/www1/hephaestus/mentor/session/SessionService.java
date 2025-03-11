package de.tum.in.www1.hephaestus.mentor.session;

import de.tum.in.www1.hephaestus.config.IntelligenceServiceConfig.IntelligenceServiceApi;
import de.tum.in.www1.hephaestus.core.exception.AccessForbiddenException;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestBaseInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.mentor.message.MessageService;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private MessageService messageService;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private IntelligenceServiceApi intelligenceServiceApi;

    public void checkAccessElseThrow(User user, Session session) {
        if (!session.getUser().getId().equals(user.getId())) {
            throw new AccessForbiddenException("Session", session.getId());
        }
    }

    public List<SessionDTO> findAllSessionsByUser(User user) {
        List<Session> sessions = sessionRepository.findByUser(user);
        return sessions.stream().map(SessionDTO::fromSession).toList();
    }

    public List<SessionDTO> findAllSessionsByUserByCreatedAtDesc(User user) {
        List<Session> sessions = sessionRepository.findByUserOrderByCreatedAtDesc(user);
        return sessions.stream().map(SessionDTO::fromSession).toList();
    }

    public Optional<SessionDTO> findSessionById(Long sessionId) {
        return sessionRepository.findById(sessionId).map(SessionDTO::fromSession);
    }

    @Transactional
    public SessionDTO createSession(User user) {
        // if the intelligence service is not available, return null
        try {
            intelligenceServiceApi.statusMentorHealthGet();
        } catch (Exception e) {
            return null;
        }
        System.out.println("Creating session for user " + user.getLogin());

        Session previousSession = sessionRepository.findFirstByUserOrderByCreatedAtDesc(user).orElse(null);
        // close the previous session if it exists to prevent multiple open sessions
        if (previousSession != null) {
            previousSession.setClosed(true);
            sessionRepository.save(previousSession);
        }
        String previousSessionId = previousSession != null ? previousSession.getId().toString() : "";

        System.out.println("Previous session ID: " + previousSessionId);

        // get the last time interval's PRs
        List<PullRequestBaseInfoDTO> pullRequests = pullRequestRepository
            .findAssignedByLoginAndStatesUpdatedSince(
                user.getLogin(),
                Set.of(Issue.State.OPEN, Issue.State.CLOSED),
                OffsetDateTime.now().minusDays(7)
            )
            .stream()
            .map(PullRequestBaseInfoDTO::fromPullRequest)
            .toList();
        String devProgress = formatPullRequests(pullRequests);

        System.out.println("Dev progress: " + devProgress);

        Session session = new Session();
        session.setUser(user);
        Session savedSession = sessionRepository.save(session);
        messageService.sendFirstMessage(session, previousSessionId, devProgress);

        System.out.println("Session created with ID " + savedSession.getId());

        return SessionDTO.fromSession(savedSession);
    }

    private String formatPullRequests(List<PullRequestBaseInfoDTO> pullRequests) {
        return pullRequests
            .stream()
            .map(pr ->
                String.format(
                    "PR\nNumber: %d\nTitle: %s\nState: %s\nDraft: %b\nMerged: %b\nURL: %s\n",
                    pr.number(),
                    pr.title(),
                    pr.state(),
                    pr.isDraft(),
                    pr.isMerged(),
                    pr.htmlUrl()
                )
            )
            .collect(Collectors.joining("\n---\n")); // add separators between PRs
    }
}
