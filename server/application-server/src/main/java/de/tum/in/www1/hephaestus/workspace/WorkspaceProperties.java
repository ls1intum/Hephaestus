package de.tum.in.www1.hephaestus.workspace;

import java.util.ArrayList;
import java.util.List;
import org.kohsuke.github.GHRepositorySelection;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hephaestus.workspace")
public class WorkspaceProperties {

    private boolean initDefault = true;
    private final DefaultWorkspace defaultWorkspace = new DefaultWorkspace();
    private final Slug slug = new Slug();

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

    public Slug getSlug() {
        return slug;
    }

    public static class Slug {

        private final Redirect redirect = new Redirect();

        public Redirect getRedirect() {
            return redirect;
        }

        public static class Redirect {

            private int ttlDays = 30;

            public int getTtlDays() {
                return ttlDays;
            }

            public void setTtlDays(int ttlDays) {
                this.ttlDays = ttlDays;
            }
        }
    }

    public static class DefaultWorkspace {

        private String login;
        private String token;
        private GHRepositorySelection repositorySelection = GHRepositorySelection.SELECTED;
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

        public GHRepositorySelection getRepositorySelection() {
            return repositorySelection;
        }

        public void setRepositorySelection(GHRepositorySelection repositorySelection) {
            this.repositorySelection = repositorySelection;
        }

        public List<String> getRepositoriesToMonitor() {
            return repositoriesToMonitor;
        }
    }
}
