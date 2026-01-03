package de.tum.in.www1.hephaestus.shared.badpractice;

import java.util.List;

/**
 * DTO for bad practice notification data. This record lives in a shared package
 * to break the circular dependency between activity and notification modules.
 */
public record BadPracticeNotificationData(
    String userLogin,
    String userEmail,
    int pullRequestNumber,
    String pullRequestTitle,
    String pullRequestUrl,
    String repositoryName,
    String workspaceSlug,
    List<BadPracticeInfo> badPractices
) {}
