package de.tum.in.www1.hephaestus.admin;

import java.util.Set;

import org.springframework.lang.NonNull;

public record AdminConfigDTO(@NonNull Set<String> repositoriesToMonitor) {
    public static AdminConfigDTO fromAdminConfig(AdminConfig adminConfig) {
        return new AdminConfigDTO(adminConfig.getRepositoriesToMonitor());
    }
}
