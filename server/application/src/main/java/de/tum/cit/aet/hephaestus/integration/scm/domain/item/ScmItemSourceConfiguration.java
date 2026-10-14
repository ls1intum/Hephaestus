package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnCaptureRole
public class ScmItemSourceConfiguration {
    @Bean
    ItemSource scmIssueItems(ScmItemService items) {
        return new ScmItemSource(ScmItemEvents.ISSUE, items);
    }

    @Bean
    ItemSource scmPullRequestItems(ScmItemService items) {
        return new ScmItemSource(ScmItemEvents.PULL_REQUEST, items);
    }

    @Bean
    ItemSource scmPullRequestReviewItems(ScmItemService items) {
        return new ScmItemSource(ScmItemEvents.PULL_REQUEST_REVIEW, items);
    }

    @Bean
    ItemSource scmRepositoryItems(ScmItemService items) {
        return new ScmItemSource(ScmItemEvents.REPOSITORY, items);
    }

    @Bean
    ItemSource scmFileItems(ScmItemService items) {
        return new ScmItemSource(ScmItemEvents.FILE, items);
    }
}
