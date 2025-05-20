package de.tum.in.www1.hephaestus.gitprovider.teamV2.permission;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TeamRepositoryPermissionId implements Serializable {
    private Long teamId;
    private Long repositoryId;
}
