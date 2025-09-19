package de.tum.in.www1.hephaestus.organization;

import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserSyncService;
import de.tum.in.www1.hephaestus.organization.github.GitHubOrganizationConverter;
import lombok.RequiredArgsConstructor;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class OrganizationSyncService {
    private final OrganizationRepository organizationRepository;
    private final GitHubOrganizationConverter organizationConverter;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final GitHubAppTokenService tokenService;
    private final GitHubUserSyncService userSyncService;

    private static final Logger logger = LoggerFactory.getLogger(OrganizationSyncService.class);

    @Transactional
    public Organization syncOrganization(GHOrganization ghOrg) {
        Organization organization = organizationRepository
            .findByGithubId(ghOrg.getId())
            .orElseGet(Organization::new);
        organizationConverter.update(ghOrg, organization);

        return organizationRepository.save(organization);
    }

    @Transactional
    public void syncByInstallationId(Long installationId) {
        Organization org = organizationRepository.findByInstallationId(installationId)
            .orElseThrow(() -> new IllegalStateException("Organization not found for installation " + installationId));

        try {
            GitHub gitHub = tokenService.clientForInstallation(installationId);
            GHOrganization ghOrg = gitHub.getOrganization(org.getLogin());
            syncOrganization(ghOrg);
        } catch (IOException e) {
            throw new RuntimeException("Failed to sync organization for installation " + installationId, e);
        }
    }

    @Transactional
    public void syncMembersByInstallationId(Long installationId) {
        Organization org = organizationRepository.findByInstallationId(installationId)
            .orElseThrow(() -> new IllegalStateException("Organization not found for installation " + installationId));

        try {
            GitHub gh = tokenService.clientForInstallation(installationId);
            GHOrganization ghOrg = gh.getOrganization(org.getLogin());

            // fetch admins + members
            List<GHUser> admins  = ghOrg.listMembersWithRole(GHOrganization.Role.ADMIN.toString()).withPageSize(100).toList();
            List<GHUser> members = ghOrg.listMembersWithRole(GHOrganization.Role.MEMBER.toString()).withPageSize(100).toList();

            // highest role per login (ADMIN > MEMBER)
            Map<String, String> desiredRoleByLoginLower = new HashMap<>();
            for (GHUser u : members) desiredRoleByLoginLower.put(u.getLogin().toLowerCase(Locale.ROOT), "MEMBER");
            for (GHUser u : admins)  desiredRoleByLoginLower.put(u.getLogin().toLowerCase(Locale.ROOT), "ADMIN");

            if (desiredRoleByLoginLower.isEmpty()) {
                List<Long> existing = membershipRepository.findUserIdsByOrganizationId(org.getGithubId());
                if (!existing.isEmpty()) {
                    membershipRepository.deleteByOrganizationIdAndUserIdIn(org.getGithubId(), existing);
                }
                logger.info("Org members synced: orgId={} total=0 (cleared {})", org.getGithubId(), existing.size());
                return;
            }

            // bulk-resolve existing users
            Set<String> loginsLower = desiredRoleByLoginLower.keySet();
            List<User> existingUsers = userRepository.findAllByLoginLowerIn(loginsLower);
            Map<String, User> byLoginLower = existingUsers.stream()
                .collect(Collectors.toMap(u -> u.getLogin().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));

            List<User> toCreate = new ArrayList<>();
            for (GHUser ghUser : Stream.concat(admins.stream(), members.stream()).toList()) {
                String loginLower = ghUser.getLogin().toLowerCase(Locale.ROOT);
                if (!byLoginLower.containsKey(loginLower)) {
                    User u = userSyncService.syncUser(loginLower);
                    if (u!= null) {
                        toCreate.add(u);
                        byLoginLower.put(loginLower, u);
                    }
                }
            }
            if (!toCreate.isEmpty()) {
                userRepository.saveAll(toCreate);
                for (User u : toCreate) {
                    byLoginLower.put(u.getLogin().toLowerCase(Locale.ROOT), u);
                }
            }

            // upsert memberships and track seen users
            Set<Long> seen = new HashSet<>();
            for (Map.Entry<String, String> e : desiredRoleByLoginLower.entrySet()) {
                User u = byLoginLower.get(e.getKey());
                if (u == null) continue;
                membershipRepository.upsertMembership(org.getGithubId(), u.getId(), e.getValue());
                seen.add(u.getId());
            }

            // remove stale memberships
            List<Long> current = membershipRepository.findUserIdsByOrganizationId(org.getGithubId());
            List<Long> toRemove = current.stream().filter(id -> !seen.contains(id)).toList();
            if (!toRemove.isEmpty()) {
                membershipRepository.deleteByOrganizationIdAndUserIdIn(org.getGithubId(), toRemove);
            }

            logger.info("Org members synced: orgId={} total={}, createdUsers={}, removedMemberships={}",
                org.getGithubId(), seen.size(), toCreate.size(), toRemove.size());

        } catch (IOException e) {
            throw new RuntimeException("Failed to sync org members for " + org.getLogin(), e);
        }
    }
}
