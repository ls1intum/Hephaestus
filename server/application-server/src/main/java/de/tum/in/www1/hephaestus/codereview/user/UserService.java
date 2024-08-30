package de.tum.in.www1.hephaestus.codereview.user;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository actorRepository) {
        this.userRepository = actorRepository;
    }

    public Optional<User> getUser(String login) {
        logger.info("Getting user with login: " + login);
        return userRepository.findUser(login);
    }

    public Optional<UserDTO> getUserDTO(String login) {
        logger.info("Getting userDTO with login: " + login);
        return userRepository.findByLogin(login);
    }
}