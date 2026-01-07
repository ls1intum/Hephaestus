package de.tum.in.www1.hephaestus.gitprovider.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "organization_membership")
@IdClass(OrganizationMembershipId.class)
@Getter
@Setter
public class OrganizationMembership {

    @Id
    @Column(name = "organization_id")
    private Long organizationId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "role", length = 32, nullable = false)
    private String role; // ADMIN | MEMBER | BILLING_MANAGER | OUTSIDE_COLLABORATOR | UNAFFILIATED

    @Column(name = "joined_at")
    private Instant joinedAt;

    @PrePersist
    public void prePersist() {
        if (joinedAt == null) {
            joinedAt = Instant.now();
        }
    }
}
