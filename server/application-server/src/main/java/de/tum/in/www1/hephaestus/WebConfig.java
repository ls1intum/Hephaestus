package de.tum.in.www1.hephaestus;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentConverter;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryConverter;
import de.tum.in.www1.hephaestus.gitprovider.user.UserConverter;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(@NonNull FormatterRegistry registry) {
        registry.addConverter(new UserConverter());
        registry.addConverter(new RepositoryConverter());
        registry.addConverter(new PullRequestConverter());
        registry.addConverter(new PullRequestReviewConverter());
        registry.addConverter(new IssueCommentConverter());
        registry.addConverter(new PullRequestReviewCommentConverter());
    }
}