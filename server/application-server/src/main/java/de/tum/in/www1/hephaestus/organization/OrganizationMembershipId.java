package de.tum.in.www1.hephaestus.organization;

import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

@NoArgsConstructor
public class OrganizationMembershipId implements Serializable {
    private Long organizationId;
    private Long userId;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OrganizationMembershipId that)) {
            return false;
        }
        return Objects.equals(organizationId, that.organizationId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(organizationId, userId);
    }
}
