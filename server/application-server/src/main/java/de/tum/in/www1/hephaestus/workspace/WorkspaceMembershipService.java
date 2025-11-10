package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceMembershipService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceMembershipService.class);

    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final EntityManager entityManager;

    public WorkspaceMembershipService(
        WorkspaceMembershipRepository workspaceMembershipRepository,
        EntityManager entityManager
    ) {
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public WorkspaceMembership createMembership(
        Workspace workspace,
        Long userId,
        WorkspaceMembership.WorkspaceRole role
    ) {
        if (workspace == null || workspace.getId() == null) {
            throw new IllegalArgumentException("Workspace must not be null and must have an ID");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        User userReference = entityManager.find(User.class, userId);
        if (userReference == null) {
            throw new IllegalArgumentException("User not found with ID: " + userId);
        }

        WorkspaceMembership membership = new WorkspaceMembership();
        membership.setWorkspace(workspace);
        membership.setUser(userReference);
        membership.setRole(role);
        membership.setId(new WorkspaceMembership.Id(workspace.getId(), userId));

        return workspaceMembershipRepository.save(membership);
    }
}
