package de.tum.in.www1.hephaestus.codereview.user;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GHUserService {
    private static final Logger logger = LoggerFactory.getLogger(GHUser.class);

    private final GHUserRepository ghuserRepository;

    public GHUserService(GHUserRepository actorRepository) {
        this.ghuserRepository = actorRepository;
    }

    public Optional<GHUser> getGHUser(String login) {
        logger.info("Getting user with login: " + login);
        return ghuserRepository.findByLogin(login);
    }

    public Optional<GHUserDTO> getGHUserDTO(String login) {
        logger.info("Getting userDTO with login: " + login);
        return ghuserRepository.findUserDTO(login);
    }
}
