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
/*
 * Webhook coverage (organization event → organization members):
 * Supported (webhook, no extra fetch):
 * - membership.user.login/id/avatar_url ⇒ persisted in User and connected through OrganizationMembership.
 * - membership.role ⇒ stored (uppercased) in role to distinguish ADMIN/MEMBER/BILLING_MANAGER/etc.
 * - action member_added/member_removed ⇒ insert/delete OrganizationMembership rows; joined_at stamped when row is created.
 * Ignored although hub4j exposes:
 * - membership.state (active/inactive) ⇒ we treat non-active as removal to keep table trimmed to active members.
 * - membership.organization_url / html_url ⇒ redundant with Organization foreign key & metadata.
 * - sender.* audit info ⇒ consumed at handler level but not persisted per row.
 * Desired but missing in hub4j/github-api 2.0-rc.5 (available via REST/GraphQL):
 * - GraphQL OrganizationMemberRoleConnection (security manager, billing manager flags per user).
 * - REST `GET /orgs/{org}/members/{username}` → last_active, two_factor_authentication, sso_bound.
 * Requires extra fetch (out-of-scope for now):
 * - GET /orgs/{org}/outside_collaborators to reconcile collaborators lacking org membership.
 * - GET /orgs/{org}/audit-log for actor/context metadata on membership changes.
 */
