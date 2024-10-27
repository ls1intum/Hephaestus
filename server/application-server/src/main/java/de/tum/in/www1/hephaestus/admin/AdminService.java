package de.tum.in.www1.hephaestus.admin;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamService;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserTeamsDTO;

@Service
public class AdminService {
    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeamService teamService;

    @Value("${monitoring.repositories}")
    private String[] repositoriesToMonitor;

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        // make sure the admin config is present
        Optional<AdminConfig> optionalAdminConfig = adminRepository.findById(1L);
        if (optionalAdminConfig.isEmpty()) {
            logger.info("No admin config found, creating new one");
            AdminConfig newAdminConfig = new AdminConfig();
            newAdminConfig.setRepositoriesToMonitor(Set.of(repositoriesToMonitor));
            adminRepository.save(newAdminConfig);
        } else {
            // repositories should match the environment variable
            AdminConfig adminConfig = optionalAdminConfig.get();
            if (adminConfig.getRepositoriesToMonitor().stream()
                    .anyMatch(repository -> !Set.of(repositoriesToMonitor).contains(repository))) {
                logger.info("Adding missing repositories to monitor");
                adminConfig.setRepositoriesToMonitor(Set.of(repositoriesToMonitor));
                adminRepository.save(adminConfig);
            }
        }
        // make sure teams are initialized
        List<TeamInfoDTO> teams = teamService.getAllTeams();
        if (teams.isEmpty()) {
            logger.info("No teams found, creating default teams");
            teamService.createDefaultTeams();
        }
    }

    @Cacheable("config")
    public AdminConfig getAdminConfig() {
        logger.info("Getting admin config");
        return adminRepository.findAll().stream().findFirst().orElseThrow(NoAdminConfigFoundException::new);
    }

    @CacheEvict(value = "config")
    public Set<String> updateRepositories(Set<String> repositories) {
        logger.info("Updating repositories to monitor");
        AdminConfig adminConfig = getAdminConfig();
        adminConfig.setRepositoriesToMonitor(repositories);
        adminRepository.save(adminConfig);
        return adminConfig.getRepositoriesToMonitor();
    }

    public List<UserTeamsDTO> getUsersAsAdmin() {
        logger.info("Getting all users with their teams");
        return userRepository.findAllWithEagerTeams().stream().map(UserTeamsDTO::fromUser).toList();
    }

    public Optional<UserInfoDTO> addTeamToUser(String login, Long teamId) {
        logger.info("Adding team (ID: " + teamId + ") to user with login: " + login);
        Optional<User> optionalUser = userRepository.findByLogin(login);
        if (optionalUser.isEmpty()) {
            return Optional.empty();
        }
        Optional<Team> optionalTeam = teamService.getTeam(teamId);
        if (optionalTeam.isEmpty()) {
            return Optional.empty();
        }
        Team team = optionalTeam.get();
        User user = optionalUser.get();
        team.addMember(user);
        teamService.saveTeam(team);
        return Optional.of(UserInfoDTO.fromUser(user));
    }

    public Optional<UserInfoDTO> removeTeamFromUser(String login, Long teamId) {
        logger.info("Removing team (ID: " + teamId + ") from user with login: " + login);
        Optional<User> optionalUser = userRepository.findByLogin(login);
        if (optionalUser.isEmpty()) {
            return Optional.empty();
        }
        Optional<Team> optionalTeam = teamService.getTeam(teamId);
        if (optionalTeam.isEmpty()) {
            return Optional.empty();
        }
        Team team = optionalTeam.get();
        User user = optionalUser.get();
        team.removeMember(user);
        teamService.saveTeam(team);
        return Optional.of(UserInfoDTO.fromUser(user));
    }

    public TeamInfoDTO createTeam(String name, String color) {
        logger.info("Creating team with name: " + name + " and color: " + color);
        return TeamInfoDTO.fromTeam(teamService.createTeam(name, color));
    }

    public Optional<TeamInfoDTO> deleteTeam(Long teamId) {
        logger.info("Deleting team with ID: " + teamId);
        Optional<Team> optionalTeam = teamService.getTeam(teamId);
        if (optionalTeam.isEmpty()) {
            return Optional.empty();
        }
        teamService.deleteTeam(teamId);
        return Optional.of(TeamInfoDTO.fromTeam(optionalTeam.get()));
    }
}
