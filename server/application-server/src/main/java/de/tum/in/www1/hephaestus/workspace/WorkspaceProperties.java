package de.tum.in.www1.hephaestus.workspace;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hephaestus.workspace")
public class WorkspaceProperties {

    private boolean initDefault = true;
    private final DefaultWorkspace defaultWorkspace = new DefaultWorkspace();

    public boolean isInitDefault() {
        return initDefault;
    }

    public void setInitDefault(boolean initDefault) {
        this.initDefault = initDefault;
    }

    public DefaultWorkspace getDefaultWorkspace() {
        return defaultWorkspace;
    }

    /**
     * Alias required so Spring Boot can bind configuration expressed under the `default` key.
     */
    public DefaultWorkspace getDefault() {
        return defaultWorkspace;
    }

    public static class DefaultWorkspace {

        private String login;
        private String token;
        private final List<String> repositoriesToMonitor = new ArrayList<>();

        public String getLogin() {
            return login;
        }

        public void setLogin(String login) {
            this.login = login;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public List<String> getRepositoriesToMonitor() {
            return repositoriesToMonitor;
        }
    }
}
