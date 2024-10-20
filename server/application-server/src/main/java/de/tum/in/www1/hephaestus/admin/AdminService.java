package de.tum.in.www1.hephaestus.admin;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class AdminService {
    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

    private final AdminRepository adminRepository;

    @Value("${monitoring.repositories}")
    private String[] repositoriesToMonitor;

    public AdminService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        Optional<AdminConfig> optionalAdminConfig = adminRepository.findById(1L);
        if (optionalAdminConfig.isEmpty()) {
            logger.info("No admin config found, creating new one");
            AdminConfig newAdminConfig = new AdminConfig();
            newAdminConfig.setRepositoriesToMonitor(Set.of(repositoriesToMonitor));
            adminRepository.save(newAdminConfig);
        } else {
            // make sure repositories match the environment variable
            AdminConfig adminConfig = optionalAdminConfig.get();
            if (adminConfig.getRepositoriesToMonitor().stream()
                    .anyMatch(repository -> !Set.of(repositoriesToMonitor).contains(repository))) {
                logger.info("Adding missing repositories to monitor");
                adminConfig.setRepositoriesToMonitor(Set.of(repositoriesToMonitor));
                adminRepository.save(adminConfig);
            }
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
}
