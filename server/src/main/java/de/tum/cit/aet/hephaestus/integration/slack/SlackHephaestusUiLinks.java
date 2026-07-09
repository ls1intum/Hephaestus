package de.tum.cit.aet.hephaestus.integration.slack;

import de.tum.cit.aet.hephaestus.workspace.spi.WorkspaceSummaryQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackHephaestusUiLinks {

    private final WorkspaceSummaryQuery workspaceSummaryQuery;
    private final String webappUrl;

    public SlackHephaestusUiLinks(
        WorkspaceSummaryQuery workspaceSummaryQuery,
        @Value("${hephaestus.webapp.url:}") String webappUrl
    ) {
        this.workspaceSummaryQuery = workspaceSummaryQuery;
        this.webappUrl = normalize(webappUrl);
    }

    public String workspaceHomeUrl(long workspaceId) {
        if (webappUrl.isBlank()) {
            return "";
        }
        return workspaceSummaryQuery
            .findById(workspaceId)
            .map(WorkspaceSummaryQuery.WorkspaceSummary::slug)
            .filter(slug -> !slug.isBlank())
            .map(slug -> webappUrl + "/w/" + slug)
            .orElse(webappUrl);
    }

    public String userSettingsUrl() {
        if (webappUrl.isBlank()) {
            return "";
        }
        return webappUrl + "/settings";
    }

    private static String normalize(String url) {
        if (url == null) {
            return "";
        }
        return url.trim().replaceAll("/+$", "");
    }
}
