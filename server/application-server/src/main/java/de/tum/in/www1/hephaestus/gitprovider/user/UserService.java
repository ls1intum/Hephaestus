package de.tum.in.www1.hephaestus.gitprovider.user;

import java.time.OffsetDateTime;
import java.util.List;
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

    public List<User> getAllUsers() {
        logger.info("Getting all users");
        return userRepository.findAll().stream().toList();
    }

    public List<User> getAllUsersInTimeframe(OffsetDateTime after, OffsetDateTime before) {
        logger.info("Getting all users in timeframe between " + after + " and " + before);
        return userRepository.findAllInTimeframe(after, before);
    }
}
