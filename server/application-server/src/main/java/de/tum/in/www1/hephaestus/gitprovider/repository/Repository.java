package de.tum.in.www1.hephaestus.gitprovider.repository;

import java.util.HashSet;
import java.util.Set;

import org.springframework.lang.NonNull;

import de.tum.in.www1.hephaestus.gitprovider.base.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import io.micrometer.common.lang.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "repository")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Repository extends BaseGitServiceEntity {
    @NonNull
    private String name;

    @NonNull
    private String nameWithOwner;

    @Nullable
    private String description;

    @NonNull
    String defaultBranch;

    @NonNull
    private RepositoryVisibility visibility;

    @NonNull
    private String url;

    String homepage;

    @OneToMany(cascade = CascadeType.REFRESH, mappedBy = "repository")
    @ToString.Exclude
    private Set<PullRequest> pullRequests = new HashSet<>();

    public void addPullRequest(PullRequest pullRequest) {
        pullRequests.add(pullRequest);
    }
}
