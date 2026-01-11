package de.tum.in.www1.hephaestus.gitprovider.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
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

    /**
     * The user's role in the organization.
     * <p>
     * Maps to GitHub's OrganizationMemberRole enum values: ADMIN, MEMBER.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 32, nullable = false)
    private OrganizationMemberRole role;
}
