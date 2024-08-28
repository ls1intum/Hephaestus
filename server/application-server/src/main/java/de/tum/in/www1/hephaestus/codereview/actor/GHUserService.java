package de.tum.in.www1.hephaestus.codereview.actor;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GHUserService {
    private static final Logger logger = LoggerFactory.getLogger(GHUser.class);

    private final GHUserRepository actorRepository;

    public GHUserService(GHUserRepository actorRepository) {
        this.actorRepository = actorRepository;
    }

    public GHUser getActor(String login) {
        logger.info("Getting actor with login: " + login);
        return actorRepository.findByLogin(login);
    }

}
