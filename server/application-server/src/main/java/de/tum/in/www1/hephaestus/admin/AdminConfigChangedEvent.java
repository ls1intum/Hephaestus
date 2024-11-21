package de.tum.in.www1.hephaestus.admin;

import org.springframework.context.ApplicationEvent;

public class AdminConfigChangedEvent extends ApplicationEvent {
    private final AdminConfig adminConfig;

    public AdminConfigChangedEvent(Object source, AdminConfig adminConfig) {
        super(source);
        this.adminConfig = adminConfig;
    }

    public AdminConfig getAdminConfig() {
        return adminConfig;
    }
}
