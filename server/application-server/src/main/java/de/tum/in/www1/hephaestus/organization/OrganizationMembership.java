package de.tum.in.www1.hephaestus.organization;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

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

    @Column(name = "role")
    private String role; // OWNER | ADMIN | MEMBER

    @Column(name = "joined_at")
    private OffsetDateTime joinedAt;

    @PrePersist
    public void prePersist() {
        if (joinedAt == null) {
            joinedAt = OffsetDateTime.now();
        }
    }
}
