package de.tum.in.www1.hephaestus.gitprovider.organization;

import de.tum.in.www1.hephaestus.gitprovider.organization.github.GitHubOrganizationConverter;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserSyncService;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceGitHubAccess;
import de.tum.in.www1.hephaestus.workspace.WorkspaceGitHubAccess.Context;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationSyncService {

    private final OrganizationRepository organizationRepository;
    private final GitHubOrganizationConverter organizationConverter;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final GitHubUserSyncService userSyncService;
    private final WorkspaceGitHubAccess workspaceGitHubAccess;
    private final GitHubUserConverter userConverter;

    public OrganizationSyncService(
        OrganizationRepository organizationRepository,
        GitHubOrganizationConverter organizationConverter,
        OrganizationMembershipRepository membershipRepository,
        UserRepository userRepository,
        WorkspaceRepository workspaceRepository,
        GitHubUserSyncService userSyncService,
        WorkspaceGitHubAccess workspaceGitHubAccess,
        GitHubUserConverter userConverter
    ) {
        this.organizationRepository = organizationRepository;
        this.organizationConverter = organizationConverter;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.userSyncService = userSyncService;
        this.workspaceGitHubAccess = workspaceGitHubAccess;
        this.userConverter = userConverter;
    }

    private static final Logger logger = LoggerFactory.getLogger(OrganizationSyncService.class);

    @Transactional
    public Optional<Organization> syncOrganization(Workspace workspace) {
        return workspaceGitHubAccess.resolve(workspace).map(this::upsertOrganization);
    }

    @Transactional
    public void syncMembers(Workspace workspace) {
        workspaceGitHubAccess.resolve(workspace).ifPresent(context -> synchronizeMembers(context));
    }

    @Transactional
    public String upsertMemberFromEvent(long organizationId, GHUser ghUser, String rawRole) {
        User user = upsertUserFromPayload(ghUser);
        String role = normalizeRole(rawRole);
        membershipRepository.upsertMembership(organizationId, user.getId(), role);
        return role;
    }

    @Transactional
    public void removeMember(long organizationId, long userId) {
        membershipRepository.deleteByOrganizationIdAndUserIdIn(organizationId, List.of(userId));
    }

    private Organization upsertOrganization(Context context) {
        GHOrganization ghOrg = context.ghOrganization();

        Organization organization = organizationRepository
            .findByGithubId(ghOrg.getId())
            .or(() -> organizationRepository.findByLoginIgnoreCase(ghOrg.getLogin()))
            .orElseGet(Organization::new);

        organizationConverter.update(ghOrg, organization);
        if (context.workspace().getInstallationId() != null) {
            organization.setInstallationId(context.workspace().getInstallationId());
        }

        organization = organizationRepository.save(organization);

        Workspace workspace = context.workspace();
        if (
            workspace.getOrganization() == null ||
            !Objects.equals(workspace.getOrganization().getId(), organization.getId())
        ) {
            workspace.setOrganization(organization);
            workspaceRepository.save(workspace);
        }

        return organization;
    }

    private void synchronizeMembers(Context context) {
        Organization organization = upsertOrganization(context);
        GHOrganization ghOrg = context.ghOrganization();

        List<GHUser> admins;
        List<GHUser> members;
        try {
            admins = ghOrg.listMembersWithRole(GHOrganization.Role.ADMIN.toString()).withPageSize(100).toList();
            members = ghOrg.listMembersWithRole(GHOrganization.Role.MEMBER.toString()).withPageSize(100).toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to list members for organization " + ghOrg.getLogin(), e);
        }

        Map<String, String> desiredRoleByLoginLower = new HashMap<>();
        for (GHUser user : members) {
            desiredRoleByLoginLower.put(user.getLogin().toLowerCase(Locale.ROOT), "MEMBER");
        }
        for (GHUser user : admins) {
            desiredRoleByLoginLower.put(user.getLogin().toLowerCase(Locale.ROOT), "ADMIN");
        }

        if (desiredRoleByLoginLower.isEmpty()) {
            List<Long> existing = membershipRepository.findUserIdsByOrganizationId(organization.getGithubId());
            if (!existing.isEmpty()) {
                membershipRepository.deleteByOrganizationIdAndUserIdIn(organization.getGithubId(), existing);
            }
            logger.info(
                "Org members synced: orgId={} total=0 (cleared {})",
                organization.getGithubId(),
                existing.size()
            );
            return;
        }

        Set<String> loginsLower = desiredRoleByLoginLower.keySet();
        List<User> existingUsers = userRepository.findAllByLoginLowerIn(loginsLower);
        Map<String, User> byLoginLower = existingUsers
            .stream()
            .collect(Collectors.toMap(u -> u.getLogin().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));

        List<User> toCreate = new ArrayList<>();
        for (GHUser ghUser : Stream.concat(admins.stream(), members.stream()).toList()) {
            String loginLower = ghUser.getLogin().toLowerCase(Locale.ROOT);
            if (!byLoginLower.containsKey(loginLower)) {
                User synced = userSyncService.syncUser(context.gitHub(), ghUser.getLogin());
                if (synced != null) {
                    toCreate.add(synced);
                    byLoginLower.put(loginLower, synced);
                }
            }
        }
        if (!toCreate.isEmpty()) {
            userRepository.saveAll(toCreate);
            for (User u : toCreate) {
                byLoginLower.put(u.getLogin().toLowerCase(Locale.ROOT), u);
            }
        }

        Set<Long> seen = new HashSet<>();
        for (Map.Entry<String, String> entry : desiredRoleByLoginLower.entrySet()) {
            User user = byLoginLower.get(entry.getKey());
            if (user == null) {
                continue;
            }
            membershipRepository.upsertMembership(organization.getGithubId(), user.getId(), entry.getValue());
            seen.add(user.getId());
        }

        List<Long> current = membershipRepository.findUserIdsByOrganizationId(organization.getGithubId());
        List<Long> toRemove = current.stream().filter(id -> !seen.contains(id)).toList();
        if (!toRemove.isEmpty()) {
            membershipRepository.deleteByOrganizationIdAndUserIdIn(organization.getGithubId(), toRemove);
        }

        logger.info(
            "Org members synced: orgId={} total={}, createdUsers={}, removedMemberships={}",
            organization.getGithubId(),
            seen.size(),
            toCreate.size(),
            toRemove.size()
        );
    }

    private User upsertUserFromPayload(GHUser ghUser) {
        if (ghUser == null) {
            throw new IllegalArgumentException("GHUser required for membership upsert");
        }
        User user = userRepository.findById(ghUser.getId()).orElseGet(User::new);
        user.setId(ghUser.getId());
        userConverter.update(ghUser, user);
        return userRepository.save(user);
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "MEMBER";
        }
        return role.toUpperCase(Locale.ROOT);
    }
}
