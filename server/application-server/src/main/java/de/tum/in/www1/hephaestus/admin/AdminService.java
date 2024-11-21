package de.tum.in.www1.hephaestus.admin;

import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamService;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserTeamsDTO;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeamService teamService;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private RepositoryRepository repositoryRepository;
    @Autowired
    private LabelRepository labelRepository;

    @Value("${monitoring.repositories}")
    private String[] repositoriesToMonitor;

    @CacheEvict(value = "config")
    private AdminConfig createInitialConfig() {
        AdminConfig newAdminConfig = new AdminConfig();
        newAdminConfig.setRepositoriesToMonitor(Set.of(repositoriesToMonitor));
        return adminRepository.save(newAdminConfig);
    }

    @Cacheable("config")
    public AdminConfig getAdminConfig() {
        logger.info("Getting admin config");
        return adminRepository.findFirstByOrderByIdAsc().orElseGet(this::createInitialConfig);
    }

    @CacheEvict(value = "config")
    public Set<String> updateRepositories(Set<String> repositories) {
        logger.info("Updating repositories to monitor");
        AdminConfig adminConfig = getAdminConfig();
        adminConfig.setRepositoriesToMonitor(repositories);
        adminRepository.save(adminConfig);

        eventPublisher.publishEvent(new AdminConfigChangedEvent(this, adminConfig));

        return adminConfig.getRepositoriesToMonitor();
    }

    public List<UserTeamsDTO> getUsersWithTeams() {
        logger.info("Getting all users with their teams");
        return userRepository.findAll().stream().map(UserTeamsDTO::fromUser).toList();
    }

    public Optional<UserInfoDTO> addTeamToUser(String login, Long teamId) {
        logger.info("Adding team (ID: " + teamId + ") to user with login: " + login);
        Optional<User> optionalUser = userRepository.findByLogin(login);
        if (optionalUser.isEmpty()) {
            return Optional.empty();
        }
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        if (optionalTeam.isEmpty()) {
            return Optional.empty();
        }
        Team team = optionalTeam.get();
        User user = optionalUser.get();
        team.addMember(user);
        teamRepository.save(team);
        return Optional.of(UserInfoDTO.fromUser(user));
    }

    public Optional<UserInfoDTO> removeUserFromTeam(String login, Long teamId) {
        logger.info("Removing team (ID: " + teamId + ") from user with login: " + login);
        Optional<User> optionalUser = userRepository.findByLogin(login);
        if (optionalUser.isEmpty()) {
            return Optional.empty();
        }
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        if (optionalTeam.isEmpty()) {
            return Optional.empty();
        }
        Team team = optionalTeam.get();
        User user = optionalUser.get();
        team.removeMember(user);
        teamRepository.save(team);
        return Optional.of(UserInfoDTO.fromUser(user));
    }

    public TeamInfoDTO createTeam(String name, String color) {
        logger.info("Creating team with name: " + name + " and color: " + color);
        return TeamInfoDTO.fromTeam(teamService.createTeam(name, color));
    }

    public Optional<TeamInfoDTO> addRepositoryToTeam(Long teamId, String repositoryName) {
        logger.info("Adding repository with name: " + repositoryName + " to team with ID: " + teamId);
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        if (optionalTeam.isEmpty()) {
            return Optional.empty();
        }
        Team team = optionalTeam.get();
        repositoryRepository.findByNameWithOwner(repositoryName).ifPresent(team::addRepository);
        teamRepository.save(team);
        return Optional.of(TeamInfoDTO.fromTeam(team));
    }

    public Optional<TeamInfoDTO> removeRepositoryFromTeam(Long teamId, String repositoryName) {
        logger.info("Removing repository with name: " + repositoryName + " from team with ID: " + teamId);
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        if (optionalTeam.isEmpty()) {
            return Optional.empty();
        }
        Team team = optionalTeam.get();
        repositoryRepository.findByNameWithOwner(repositoryName).ifPresent(team::removeRepository);
        teamRepository.save(team);
        return Optional.of(TeamInfoDTO.fromTeam(team));
    }

    public Optional<TeamInfoDTO> addLabelToTeam(Long teamId, String label) {
        logger.info("Adding label '" + label + "' to team with ID: " + teamId);
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        if (optionalTeam.isEmpty()) {
            return Optional.empty();
        }
        Team team = optionalTeam.get();
        Optional<Label> labelEntity = labelRepository.findByName(label);
        if (labelEntity.isEmpty()) {
            return Optional.empty();
        }
        team.addLabel(labelEntity.get());
        teamRepository.save(team);
        return Optional.of(TeamInfoDTO.fromTeam(team));
    }

    public Optional<TeamInfoDTO> removeLabelFromTeam(Long teamId, String label) {
        logger.info("Removing label '" + label + "' from team with ID: " + teamId);
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        if (optionalTeam.isEmpty()) {
            return Optional.empty();
        }
        Team team = optionalTeam.get();
        Optional<Label> labelEntity = labelRepository.findByName(label);
        if (labelEntity.isEmpty()) {
            return Optional.empty();
        }
        team.removeLabel(labelEntity.get());
        teamRepository.save(team);
        return Optional.of(TeamInfoDTO.fromTeam(team));
    }

    public Optional<TeamInfoDTO> deleteTeam(Long teamId) {
        logger.info("Deleting team with ID: " + teamId);
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        if (optionalTeam.isEmpty()) {
            return Optional.empty();
        }
        teamRepository.delete(optionalTeam.get());
        return Optional.of(TeamInfoDTO.fromTeam(optionalTeam.get()));
    }
}
