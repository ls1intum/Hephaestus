package de.tum.in.www1.hephaestus.gitprovider.teamV2.membership;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TeamMembershipId implements Serializable {

    private Long teamId;
    private Long userId;
}
