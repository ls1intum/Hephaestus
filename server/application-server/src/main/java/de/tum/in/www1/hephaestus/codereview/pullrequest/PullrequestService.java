package de.tum.in.www1.hephaestus.codereview.pullrequest;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PullRequestService {
    private static final Logger logger = LoggerFactory.getLogger(PullRequestService.class);

    private final PullRequestRepository pullrequestRepository;

    public PullRequestService(PullRequestRepository pullrequestRepository) {
        this.pullrequestRepository = pullrequestRepository;
    }

    public PullRequest getPullRequestById(Long id) {
        logger.info("Getting pullrequest with id: " + id);
        return pullrequestRepository.findById(id).orElse(null);
    }

    public Set<PullRequest> getPullRequestsByAuthor(String login) {
        logger.info("Getting pullrequest by author: " + login);
        return pullrequestRepository.findByAuthor_Login(login);
    }

}
