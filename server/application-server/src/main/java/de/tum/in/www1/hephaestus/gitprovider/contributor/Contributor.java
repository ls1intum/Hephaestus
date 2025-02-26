package de.tum.in.www1.hephaestus.gitprovider.contributor;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

@Entity
@Table(name = "contributor")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Contributor extends BaseGitServiceEntity {

    @ManyToOne
    @JoinColumn(name = "repository_id")
    @ToString.Exclude
    @NonNull
    private Repository repository;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    @NonNull
    private User user;

    private int contributions;
}
