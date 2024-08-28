package de.tum.in.www1.hephaestus.codereview.pullrequest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PullRequestService {
    private static final Logger logger = LoggerFactory.getLogger(PullRequest.class);

    private final PullRequestRepository pullrequestRepository;

    public PullRequestService(PullRequestRepository pullrequestRepository) {
        this.pullrequestRepository = pullrequestRepository;
    }

    public PullRequest getPullrequestById(Long id) {
        logger.info("Getting pullrequest with id: " + id);
        return pullrequestRepository.findById(id).orElse(null);
    }

    public List<PullRequest> getPullrequestsByAuthor(String login) {
        logger.info("Getting pullrequest by author: " + login);
        return pullrequestRepository.findByAuthor(login);
    }

}
