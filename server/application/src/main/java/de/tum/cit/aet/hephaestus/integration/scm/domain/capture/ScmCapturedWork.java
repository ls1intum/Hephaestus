package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import org.jspecify.annotations.Nullable;

public record ScmCapturedWork(
        long sourceId,
        long repositoryId,
        int number,
        @Nullable String url,
        String repositoryPath,
        String title,
        @Nullable String body,
        @Nullable String head,
        @Nullable String base,
        @Nullable String sourceBranch,
        @Nullable String targetBranch,
        @Nullable Long reviewSourceId,
        @Nullable Long reviewerUserId) {
    public ScmCapturedWork {
        if (sourceId <= 0 || repositoryId <= 0 || number <= 0 || repositoryPath.isBlank())
            throw new IllegalArgumentException("Invalid pinned SCM work");
    }
}
