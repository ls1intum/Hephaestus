package de.tum.in.www1.hephaestus.codereview.actor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ActorService {
    private static final Logger logger = LoggerFactory.getLogger(Actor.class);

    private final ActorRepository actorRepository;

    public ActorService(ActorRepository actorRepository) {
        this.actorRepository = actorRepository;
    }

    public Actor getActor(String login) {
        logger.info("Getting actor with login: " + login);
        return actorRepository.findByLogin(login);
    }

}
