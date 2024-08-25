package de.tum.in.www1.hephaestus.codereview.pullrequest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PullrequestService {
    private static final Logger logger = LoggerFactory.getLogger(Pullrequest.class);

    private final PullrequestRepository pullrequestRepository;

    public PullrequestService(PullrequestRepository pullrequestRepository) {
        this.pullrequestRepository = pullrequestRepository;
    }

    public Pullrequest getPullrequestById(Long id) {
        logger.info("Getting pullrequest with id: " + id);
        return pullrequestRepository.findById(id).orElse(null);
    }

    public List<Pullrequest> getPullrequestsByAuthor(String login) {
        logger.info("Getting pullrequest by author: " + login);
        return pullrequestRepository.findByAuthor(login);
    }

}
