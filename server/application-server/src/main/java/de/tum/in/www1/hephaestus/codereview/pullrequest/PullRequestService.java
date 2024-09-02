package de.tum.in.www1.hephaestus.codereview.pullrequest;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PullRequestService {
    private static final Logger logger = LoggerFactory.getLogger(PullRequestService.class);

    private final PullRequestRepository pullRequestRepository;

    public PullRequestService(PullRequestRepository pullRequestRepository) {
        this.pullRequestRepository = pullRequestRepository;
    }

    public Optional<PullRequest> getPullRequestById(Long id) {
        logger.info("Getting pullRequest with id: {}", id);
        return pullRequestRepository.findById(id);
    }

    public Set<PullRequest> getPullRequestsByAuthor(String login) {
        logger.info("Getting pullRequest by author: {}", login);
        return pullRequestRepository.findByAuthor_Login(login);
    }

}
