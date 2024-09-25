package de.tum.in.www1.hephaestus;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import de.tum.in.www1.hephaestus.codereview.comment.IssueCommentConverter;
import de.tum.in.www1.hephaestus.codereview.comment.review.PullRequestReviewCommentConverter;
import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequestConverter;
import de.tum.in.www1.hephaestus.codereview.pullrequest.review.PullRequestReviewConverter;
import de.tum.in.www1.hephaestus.codereview.repository.RepositoryConverter;
import de.tum.in.www1.hephaestus.codereview.user.UserConverter;

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